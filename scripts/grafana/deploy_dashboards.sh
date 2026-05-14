#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-bwvnOk7cFcdlyCHQz9MaqHYBsSJJ5NGrK23UedCG}"
GRAFANA_TOKEN="${GRAFANA_TOKEN:-}"
INCLUDE_GLOBAL_DASHBOARDS="${INCLUDE_GLOBAL_DASHBOARDS:-true}"

command -v curl >/dev/null || {
  echo "curl is required" >&2
  exit 1
}

command -v jq >/dev/null || {
  echo "jq is required" >&2
  exit 1
}

auth_args=()
if [[ -n "${GRAFANA_TOKEN}" ]]; then
  auth_args=(-H "Authorization: Bearer ${GRAFANA_TOKEN}")
else
  auth_args=(-u "${GRAFANA_USER}:${GRAFANA_PASSWORD}")
fi

api() {
  local method="$1"
  local path="$2"
  local payload="${3:-}"
  if [[ -n "${payload}" ]]; then
    curl -sS --fail-with-body "${auth_args[@]}" \
      -H "Content-Type: application/json" \
      -X "${method}" \
      "${GRAFANA_URL}${path}" \
      -d "${payload}"
  else
    curl -sS --fail-with-body "${auth_args[@]}" \
      -H "Content-Type: application/json" \
      -X "${method}" \
      "${GRAFANA_URL}${path}"
  fi
}

ensure_folder() {
  local uid="$1"
  local title="$2"
  local payload
  payload="$(jq -n --arg uid "${uid}" --arg title "${title}" '{uid: $uid, title: $title}')"
  if ! api POST /api/folders "${payload}" >/dev/null 2>&1; then
    api GET "/api/folders/${uid}" >/dev/null
  fi
}

deploy_dashboard() {
  local file="$1"
  local folder_uid="$2"
  local dashboard_uid
  local existing
  local existing_folder_uid
  local existing_is_folder
  local existing_provisioned
  local payload
  dashboard_uid="$(jq -r '.uid // empty' "${file}")"
  if [[ -n "${dashboard_uid}" ]]; then
    if ! existing="$(api GET "/api/dashboards/uid/${dashboard_uid}" 2>/dev/null)"; then
      existing=""
    fi
    if [[ -n "${existing}" ]]; then
      existing_is_folder="$(jq -r '.meta.isFolder // false' <<<"${existing}")"
      if [[ "${existing_is_folder}" == "true" ]]; then
        api DELETE "/api/folders/${dashboard_uid}" >/dev/null
        existing=""
      fi
    fi
    if [[ -n "${existing}" ]]; then
      existing_provisioned="$(jq -r '.meta.provisioned // false' <<<"${existing}")"
      if [[ "${existing_provisioned}" == "true" ]]; then
        echo "skipped provisioned ${file#${ROOT_DIR}/}"
        return 0
      fi
      existing_folder_uid="$(jq -r '.meta.folderUid // ""' <<<"${existing}")"
      if [[ "${existing_folder_uid}" != "${folder_uid}" ]]; then
        api DELETE "/api/dashboards/uid/${dashboard_uid}" >/dev/null
      fi
    fi
  fi
  payload="$(jq -c --arg folderUid "${folder_uid}" '{
    dashboard: (. + {id: null, uid: (.uid // null)}),
    folderUid: $folderUid,
    message: "Deployed by scripts/grafana/deploy_dashboards.sh",
    overwrite: true
  }' "${file}")"
  api POST /api/dashboards/db "${payload}" >/dev/null
  echo "deployed ${file#${ROOT_DIR}/}"
}

deploy_service_dashboards() {
  local services=(
    bank-flow-accounts
    bank-flow-outboxer
    bank-flow-transfer
    bank-flow-ledger
    bank-flow-balance
    bank-flow-yield
  )
  local service
  local dashboards_dir
  local file
  for service in "${services[@]}"; do
    dashboards_dir="${ROOT_DIR}/${service}/dashboards"
    [[ -d "${dashboards_dir}" ]] || continue
    ensure_folder "${service}-dashboards" "${service} dashboards"
    while IFS= read -r file; do
      deploy_dashboard "${file}" "${service}-dashboards"
    done < <(find "${dashboards_dir}" -maxdepth 1 -type f -name "*.json" | sort)
  done
}

deploy_global_dashboards() {
  [[ "${INCLUDE_GLOBAL_DASHBOARDS}" == "true" ]] || return 0
  ensure_folder bank-flow-dashboards "Bank Flow Dashboards"
  while IFS= read -r file; do
    deploy_dashboard "${file}" bank-flow-dashboards
  done < <(find "${ROOT_DIR}/observability/grafana/dashboards" -maxdepth 1 -type f -name "*.json" | sort)
}

api GET /api/health >/dev/null || {
  echo "Grafana is not reachable at ${GRAFANA_URL}" >&2
  echo "Start Grafana or set GRAFANA_URL before running this script." >&2
  exit 1
}

deploy_service_dashboards
deploy_global_dashboards
