#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NAMESPACE="${OBSERVABILITY_NAMESPACE:-monitoring}"

echo "Creating namespace ${NAMESPACE} if needed..."
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "Configuring Helm repositories..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo add grafana https://grafana.github.io/helm-charts >/dev/null
helm repo update

echo "Installing kube-prometheus-stack..."
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace "${NAMESPACE}" \
  --create-namespace \
  -f "${ROOT_DIR}/observability/k8s/kube-prometheus-stack-values.yaml"

echo "Installing prometheus-blackbox-exporter..."
helm upgrade --install prometheus-blackbox-exporter prometheus-community/prometheus-blackbox-exporter \
  --namespace "${NAMESPACE}"

echo "Installing Loki..."
helm upgrade --install loki grafana/loki \
  --namespace "${NAMESPACE}" \
  -f "${ROOT_DIR}/observability/k8s/loki-values.yaml"

echo "Installing Grafana Alloy..."
helm upgrade --install alloy grafana/alloy \
  --namespace "${NAMESPACE}" \
  -f "${ROOT_DIR}/observability/k8s/alloy-values.yaml"

echo "Installing Tempo..."
helm upgrade --install tempo grafana/tempo \
  --namespace "${NAMESPACE}" \
  -f "${ROOT_DIR}/observability/k8s/tempo-values.yaml"

echo "Waiting for observability workloads..."
kubectl rollout status -n "${NAMESPACE}" deployment/monitoring-grafana --timeout=180s
kubectl rollout status -n "${NAMESPACE}" statefulset/loki --timeout=180s
kubectl rollout status -n "${NAMESPACE}" daemonset/alloy --timeout=180s
kubectl rollout status -n "${NAMESPACE}" statefulset/tempo --timeout=180s

echo "Observability stack is installed in namespace ${NAMESPACE}."
