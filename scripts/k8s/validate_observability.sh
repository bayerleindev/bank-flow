#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${OBSERVABILITY_NAMESPACE:-monitoring}"
RUN_ID="$(date +%s)"

echo "Checking observability pods..."
kubectl get pods -n "${NAMESPACE}" \
  -l 'app.kubernetes.io/name in (grafana,loki,alloy,tempo)'

echo
echo "Checking observability services..."
kubectl get svc -n "${NAMESPACE}" loki-gateway tempo monitoring-grafana

echo
echo "Checking Grafana datasources..."
kubectl get configmap -n "${NAMESPACE}" monitoring-kube-prometheus-grafana-datasource -o yaml | \
  grep -E 'name: (Loki|Tempo)|uid: (Loki|tempo)|loki-gateway|tempo\.monitoring|tracesToLogsV2|derivedFields|serviceMap'

echo
echo "Querying Loki labels from inside Kubernetes..."
kubectl run -n "${NAMESPACE}" "curl-loki-check-${RUN_ID}" \
  --rm -i --restart=Never \
  --image=curlimages/curl:8.10.1 -- \
  curl -fsS "http://loki-gateway.${NAMESPACE}.svc.cluster.local/loki/api/v1/labels"

echo
echo
echo "Querying Tempo search tags from inside Kubernetes..."
kubectl run -n "${NAMESPACE}" "curl-tempo-check-${RUN_ID}" \
  --rm -i --restart=Never \
  --image=curlimages/curl:8.10.1 -- \
  curl -fsS "http://tempo.${NAMESPACE}.svc.cluster.local:3200/api/v2/search/tags?limit=20"

echo
echo
echo "Querying Loki through Grafana datasource proxy..."
GRAFANA_PASSWORD="$(kubectl get secret -n "${NAMESPACE}" monitoring-grafana -o go-template='{{ index .data "admin-password" | base64decode }}')"
GRAFANA_AUTH="$(printf 'admin:%s' "${GRAFANA_PASSWORD}" | base64)"
kubectl exec -n "${NAMESPACE}" deployment/monitoring-grafana -c grafana -- \
  wget -qO- \
    --header "Authorization: Basic ${GRAFANA_AUTH}" \
    'http://localhost:3000/api/datasources/proxy/uid/Loki/loki/api/v1/query_range?query=%7Bnamespace%3D%22bank-flow%22%7D&limit=3'

echo
echo
echo "Observability validation completed."
echo "Useful Grafana Explore queries:"
echo '  {namespace="bank-flow"}'
echo '  {stack="bank-flow"}'
echo '  {application="bank-flow-transfer-api"}'
echo '  {transfer_id!="", namespace="bank-flow"}'
