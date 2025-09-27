#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)"
PAYLOAD_DIR="${SCRIPT_DIR}/seed-historical/payloads"

BASE_URL=${BASE_URL:-http://localhost:8080}
ADMIN_USERNAME=${ADMIN_USERNAME:-admin1}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-password}
MAKER_USERNAME=${MAKER_USERNAME:-ops1}
MAKER_PASSWORD=${MAKER_PASSWORD:-password}
DAYS=30
RUNS_PER_DAY=3
SKIP_EXPORT_CHECK=false

usage() {
  cat <<'USAGE'
Usage: ./scripts/verify-historical-seed.sh [options]

Options:
  --base-url <url>         Platform base URL (default: http://localhost:8080)
  --admin-user <username>  Admin username (default: admin1)
  --admin-pass <password>  Admin password (default: password)
  --maker-user <username>  Maker/checker username (default: ops1)
  --maker-pass <password>  Maker/checker password (default: password)
  --days <n>               Expected history length in days (default: 30)
  --runs-per-day <n>       Expected runs per day (default: 3)
  --skip-export-check      Skip validation of completed export jobs
  -h, --help               Show help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"; shift 2 ;;
    --admin-user)
      ADMIN_USERNAME="$2"; shift 2 ;;
    --admin-pass)
      ADMIN_PASSWORD="$2"; shift 2 ;;
    --maker-user)
      MAKER_USERNAME="$2"; shift 2 ;;
    --maker-pass)
      MAKER_PASSWORD="$2"; shift 2 ;;
    --days)
      DAYS="$2"; shift 2 ;;
    --runs-per-day)
      RUNS_PER_DAY="$2"; shift 2 ;;
    --skip-export-check)
      SKIP_EXPORT_CHECK=true; shift ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      printf '[verify-seed] Unknown option %s\n' "$1" >&2
      exit 1 ;;
  esac
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf '[verify-seed] Missing required command %s\n' "$1" >&2
    exit 1
  fi
}

require_command curl
require_command jq
require_command python3

PAYLOAD_FILES=()
while IFS= read -r payload_file; do
  PAYLOAD_FILES+=("$payload_file")
done < <(find "$PAYLOAD_DIR" -type f -name '*.json' | sort)
if [[ ${#PAYLOAD_FILES[@]} -lt 5 ]]; then
  printf '[verify-seed] Expected at least five payloads in %s\n' "$PAYLOAD_DIR" >&2
  exit 1
fi

obtain_token() {
  local username="$1" password="$2"
  local response
  response=$(curl -sS -w '\n%{http_code}' -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\"}")
  local body="${response%$'\n'*}"
  local status="${response##*$'\n'}"
  if [[ "$status" != "200" ]]; then
    printf '[verify-seed] Authentication failed for %s (status %s)\n' "$username" "$status" >&2
    exit 1
  fi
  echo "$body" | jq -r '.token'
}

ADMIN_TOKEN=$(obtain_token "$ADMIN_USERNAME" "$ADMIN_PASSWORD")
MAKER_TOKEN=$(obtain_token "$MAKER_USERNAME" "$MAKER_PASSWORD")
EXPECTED_RUNS=$((DAYS * RUNS_PER_DAY))

threshold_timestamp=$(python3 - <<'PY'
import datetime
utc_now = datetime.datetime.now(datetime.timezone.utc)
print((utc_now - datetime.timedelta(days=1)).isoformat())
PY
)

# Convert iso to comparable string
audited_ok=true

for payload in "${PAYLOAD_FILES[@]}"; do
  code=$(jq -r '.code' "$payload")
  response=$(curl -sS -w '\n%{http_code}' -G "$BASE_URL/api/admin/reconciliations" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    --data-urlencode "search=$code" \
    --data-urlencode "size=50")
  body="${response%$'\n'*}"
  status="${response##*$'\n'}"
  if [[ "$status" != "200" ]]; then
    printf '[verify-seed] Failed to search reconciliation %s (HTTP %s)\n' "$code" "$status" >&2
    audited_ok=false
    continue
  fi
  recon_id=$(echo "$body" | jq -r ".items[] | select(.code == \"$code\") | .id" | head -n 1)
  if [[ -z "$recon_id" || "$recon_id" == "null" ]]; then
    printf '[verify-seed] Reconciliation %s not found.\n' "$code" >&2
    audited_ok=false
    continue
  fi

  runs=$(curl -sS -H "Authorization: Bearer $MAKER_TOKEN" \
    -G "$BASE_URL/api/reconciliations/$recon_id/runs" --data-urlencode "limit=$((EXPECTED_RUNS + 5))")
  run_count=$(echo "$runs" | jq 'length')
  if (( run_count < EXPECTED_RUNS )); then
    printf '[verify-seed] %s has %d runs; expected at least %d.\n' "$code" "$run_count" "$EXPECTED_RUNS" >&2
    audited_ok=false
  fi

  old_runs=$(echo "$runs" | jq --arg threshold "$threshold_timestamp" '[.[] | select(.runDateTime < $threshold)]')
  for row in $(echo "$old_runs" | jq -c '.[]'); do
    run_id=$(echo "$row" | jq -r '.runId')
    detail=$(curl -sS -H "Authorization: Bearer $MAKER_TOKEN" "$BASE_URL/api/reconciliations/runs/$run_id")
    break_count=$(echo "$detail" | jq '.breaks | length')
    if (( break_count > 0 )); then
      open_breaks=$(echo "$detail" | jq '[.breaks[] | select(.status == "OPEN")] | length')
      if (( open_breaks > 0 )); then
        printf '[verify-seed] Run %s for %s still has %d OPEN breaks.\n' "$run_id" "$code" "$open_breaks" >&2
        audited_ok=false
      fi
      missing_comments=$(echo "$detail" | jq '[.breaks[] | select((.comments | length) == 0)] | length')
      if (( missing_comments > 0 )); then
        printf '[verify-seed] Run %s for %s has %d breaks without comments.\n' "$run_id" "$code" "$missing_comments" >&2
        audited_ok=false
      fi
      pending_history=$(echo "$detail" | jq '[.breaks[] | select((.history | length) == 0)] | length')
      if (( pending_history > 0 )); then
        printf '[verify-seed] Run %s for %s has %d breaks without workflow history.\n' "$run_id" "$code" "$pending_history" >&2
        audited_ok=false
      fi
    fi
  done

  if [[ "$SKIP_EXPORT_CHECK" != "true" ]]; then
    jobs=$(curl -sS -H "Authorization: Bearer $MAKER_TOKEN" "$BASE_URL/api/reconciliations/$recon_id/export-jobs")
    completed_jobs=$(echo "$jobs" | jq '[.[] | select(.status == "COMPLETED")] | length')
    if (( completed_jobs == 0 )); then
      printf '[verify-seed] No completed export jobs found for %s.\n' "$code" >&2
      audited_ok=false
    fi
  fi

done

if [[ "$audited_ok" != "true" ]]; then
  exit 1
fi

printf '[verify-seed] Historical seed verification succeeded.\n'
