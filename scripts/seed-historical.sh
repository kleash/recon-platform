#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PAYLOAD_DIR="${SCRIPT_DIR}/seed-historical/payloads"
GENERATOR="${SCRIPT_DIR}/seed-historical/generate_csv.py"
TMP_ROOT="$(mktemp -d 2>/dev/null || mktemp -d -t 'seed-historical')"
FIXTURE_DIR="$ROOT_DIR/examples/integration-harness/src/main/resources/data/global-multi-asset"
trap 'rm -rf "$TMP_ROOT"' EXIT

# Defaults – configurable via flags or environment overrides.
BASE_URL=${BASE_URL:-http://localhost:8080}
ADMIN_USERNAME=${ADMIN_USERNAME:-admin1}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-password}
MAKER_USERNAME=${MAKER_USERNAME:-ops1}
MAKER_PASSWORD=${MAKER_PASSWORD:-password}
DAYS=30
RUNS_PER_DAY=3
MIN_RECORDS=100
MAX_RECORDS=200
REPORT_FORMAT=CSV
POLL_ATTEMPTS=12
POLL_DELAY=2
CI_MODE=${CI_MODE:-false}

log() {
  printf '[seed-historical] %s\n' "$*"
}

fail() {
  printf '[seed-historical] ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command '$1'. Install it and retry."
  fi
}

usage() {
  cat <<'USAGE'
Usage: ./scripts/seed-historical.sh [options]

Options:
  --base-url <url>           Platform base URL (default: http://localhost:8080)
  --admin-user <username>   Admin username (default: admin1)
  --admin-pass <password>   Admin password (default: password)
  --maker-user <username>   Maker/checker username (default: ops1)
  --maker-pass <password>   Maker/checker password (default: password)
  --days <n>                How many days of history to generate (default: 30)
  --runs-per-day <n>        Runs per reconciliation per day (default: 3)
  --min-records <n>         Minimum records per batch (default: 100)
  --max-records <n>         Maximum records per batch (default: 200)
  --report-format <fmt>     Export format to queue (default: CSV, set to NONE to skip)
  --ci-mode                 Enable lightweight logging suited for CI runs
  -h, --help                Show this help
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
    --min-records)
      MIN_RECORDS="$2"; shift 2 ;;
    --max-records)
      MAX_RECORDS="$2"; shift 2 ;;
    --report-format)
      REPORT_FORMAT=$(printf '%s' "$2" | tr '[:lower:]' '[:upper:]'); shift 2 ;;
    --ci-mode)
      CI_MODE=true; shift ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      fail "Unknown option '$1'" ;;
  esac
done

if (( MIN_RECORDS <= 0 || MAX_RECORDS <= 0 )); then
  fail "Record counts must be positive."
fi
if (( MIN_RECORDS > MAX_RECORDS )); then
  fail "--min-records cannot exceed --max-records."
fi
if (( DAYS <= 0 )); then
  fail "--days must be positive."
fi
if (( RUNS_PER_DAY <= 0 )); then
  fail "--runs-per-day must be positive."
fi

require_command curl
require_command jq
require_command python3

if [[ ! -d "$PAYLOAD_DIR" ]]; then
  fail "Expected payload directory at $PAYLOAD_DIR"
fi
if [[ ! -x "$GENERATOR" ]]; then
  fail "Generator script missing or not executable at $GENERATOR"
fi

declare -A SOURCE_CONFIG=(
  ["GLOBAL_MASTER,media_type"]='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  ["GLOBAL_MASTER,adapter_type"]='EXCEL_FILE'
  ["GLOBAL_MASTER,options_json"]='{"hasHeader":true,"includeAllSheets":true,"includeSheetNameColumn":true,"sheetNameColumn":"global_sheet_tag"}'
  ["GLOBAL_MASTER,fixture_path"]="$FIXTURE_DIR/global_master.xlsx"

  ["APAC_MULTI,media_type"]='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  ["APAC_MULTI,adapter_type"]='EXCEL_FILE'
  ["APAC_MULTI,options_json"]='{"hasHeader":true,"includeAllSheets":true,"includeSheetNameColumn":true,"sheetNameColumn":"apac_sheet_tag"}'
  ["APAC_MULTI,fixture_path"]="$FIXTURE_DIR/apac_positions.xlsx"

  ["EMEA_MULTI,media_type"]='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  ["EMEA_MULTI,adapter_type"]='EXCEL_FILE'
  ["EMEA_MULTI,options_json"]='{"hasHeader":true,"includeAllSheets":true,"includeSheetNameColumn":true,"sheetNameColumn":"emea_sheet_tag"}'
  ["EMEA_MULTI,fixture_path"]="$FIXTURE_DIR/emea_positions.xlsx"

  ["AMERICAS_CASH,media_type"]='text/csv'
  ["AMERICAS_CASH,adapter_type"]='CSV_FILE'
  ["AMERICAS_CASH,options_json"]='{"delimiter":","}'
  ["AMERICAS_CASH,fixture_path"]="$FIXTURE_DIR/americas_cash.csv"

  ["DERIVATIVES_FEED,media_type"]='text/csv'
  ["DERIVATIVES_FEED,adapter_type"]='CSV_FILE'
  ["DERIVATIVES_FEED,options_json"]='{"delimiter":","}'
  ["DERIVATIVES_FEED,fixture_path"]="$FIXTURE_DIR/derivatives_positions.csv"

  ["GLOBAL_CUSTODY,media_type"]='text/plain'
  ["GLOBAL_CUSTODY,adapter_type"]='CSV_FILE'
  ["GLOBAL_CUSTODY,options_json"]='{"delimiter":"|","hasHeader":true}'
  ["GLOBAL_CUSTODY,fixture_path"]="$FIXTURE_DIR/global_custody.txt"
)

PAYLOAD_FILES=()
while IFS= read -r payload_file; do
  PAYLOAD_FILES+=("$payload_file")
done < <(find "$PAYLOAD_DIR" -type f -name '*.json' | sort)
if [[ ${#PAYLOAD_FILES[@]} -lt 5 ]]; then
  fail "Expected at least 5 reconciliation payloads in $PAYLOAD_DIR"
fi

log "Seeding ${#PAYLOAD_FILES[@]} reconciliations over $DAYS day(s) with $RUNS_PER_DAY run(s) per day."

obtain_token() {
  local username="$1" password="$2"
  local response
  response=$(curl -sS -w '\n%{http_code}' -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\"}")

  local body status
  body="${response%$'\n'*}"
  status="${response##*$'\n'}"

  if [[ "$status" != "200" ]]; then
    fail "Authentication failed for $username (status $status): $body"
  fi
  echo "$body" | jq -r '.token'
}

ADMIN_TOKEN=$(obtain_token "$ADMIN_USERNAME" "$ADMIN_PASSWORD")
MAKER_TOKEN=$(obtain_token "$MAKER_USERNAME" "$MAKER_PASSWORD")

iso_date_utc() {
  python3 - "$1" <<'PY'
import datetime
import sys
offset = int(sys.argv[1])
utc_now = datetime.datetime.now(datetime.timezone.utc)
print((utc_now - datetime.timedelta(days=offset)).date())
PY
}

run_timestamp_utc() {
  python3 - "$1" "$2" <<'PY'
import datetime
import sys
offset = int(sys.argv[1])
run_index = int(sys.argv[2])
utc_now = datetime.datetime.now(datetime.timezone.utc)
base = utc_now - datetime.timedelta(days=offset)
# Spread runs across the day by subtracting additional hours.
when = base.replace(hour=22, minute=0, second=0, microsecond=0) - datetime.timedelta(hours=run_index)
print(when.isoformat(timespec='seconds').replace('+00:00', 'Z'))
PY
}

ensure_reconciliation() {
  local payload="$1"
  local code
  code=$(jq -r '.code' "$payload")
  [[ -n "$code" && "$code" != "null" ]] || fail "Payload $payload missing code"

  local query
  query=$(curl -sS -G "$BASE_URL/api/admin/reconciliations" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    --data-urlencode "search=$code" \
    --data-urlencode "size=50")

  local recon_id
  recon_id=$(echo "$query" | jq -r ".items[] | select(.code == \"$code\") | .id" | head -n 1)

  local method url expected
  if [[ -n "$recon_id" && "$recon_id" != "null" ]]; then
    method=PUT
    url="$BASE_URL/api/admin/reconciliations/$recon_id"
    expected=200
  else
    method=POST
    url="$BASE_URL/api/admin/reconciliations"
    expected=201
  fi

  local response
  response=$(curl -sS -w '\n%{http_code}' -X "$method" "$url" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H 'Content-Type: application/json' \
    --data @"$payload")

  local body status
  body="${response%$'\n'*}"
  status="${response##*$'\n'}"

  if [[ "$status" != "$expected" ]]; then
    fail "Failed to upsert reconciliation $code (HTTP $status): $body"
  fi

  echo "$body" | jq -r '.id'
}

random_record_count() {
  if (( MIN_RECORDS == MAX_RECORDS )); then
    echo "$MIN_RECORDS"
  else
    local diff=$((MAX_RECORDS - MIN_RECORDS + 1))
    echo $((MIN_RECORDS + RANDOM % diff))
  fi
}

upload_batch() {
  local recon_id="$1" source_code="$2" file_path="$3" label="$4"
  local metadata
  metadata=$(jq -n --arg label "$label" '{adapterType:"CSV_FILE", label: $label}')
  local response
  response=$(curl -sS -w '\n%{http_code}' -X POST \
    "$BASE_URL/api/admin/reconciliations/$recon_id/sources/$source_code/batches" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -F "metadata=$metadata;type=application/json;filename=metadata.json" \
    -F "file=@$file_path;type=text/csv;filename=$label.csv")

  local body status
  body="${response%$'\n'*}"
  status="${response##*$'\n'}"
  if [[ "$status" != "201" && "$status" != "200" ]]; then
    fail "Failed to upload batch '$label' for $source_code (HTTP $status): $body"
  fi
}

upload_batch_with_metadata() {
  local recon_id="$1" source_code="$2" file_path="$3" label="$4" adapter_type="$5" options_json="$6" media_type="$7"

  local metadata
  if [[ -n "$options_json" ]]; then
    metadata=$(jq -n --arg label "$label" --arg type "$adapter_type" --argjson opts "$options_json" '{adapterType:$type,label:$label,options:$opts}')
  else
    metadata=$(jq -n --arg label "$label" --arg type "$adapter_type" '{adapterType:$type,label:$label}')
  fi

  local filename
  filename=$(basename "$file_path")

  local response
  response=$(curl -sS -w '\n%{http_code}' -X POST \
    "$BASE_URL/api/admin/reconciliations/$recon_id/sources/$source_code/batches" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -F "metadata=$metadata;type=application/json;filename=metadata.json" \
    -F "file=@$file_path;type=$media_type;filename=$filename")

  local body status
  body="${response%$'\n'*}"
  status="${response##*$'\n'}"
  if [[ "$status" != "201" && "$status" != "200" ]]; then
    fail "Failed to upload batch '$label' for $source_code (HTTP $status): $body"
  fi
}

trigger_run() {
  local recon_id="$1" comment="$2" initiated_by="$3"
  local response
  response=$(curl -sS -w '\n%{http_code}' -X POST \
    "$BASE_URL/api/reconciliations/$recon_id/run" \
    -H "Authorization: Bearer $MAKER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"triggerType\":\"MANUAL_API\",\"comments\":\"$comment\",\"initiatedBy\":\"$initiated_by\"}")

  local body status
  body="${response%$'\n'*}"
  status="${response##*$'\n'}"
  if [[ "$status" != "200" ]]; then
    fail "Failed to trigger run for reconciliation $recon_id (HTTP $status): $body"
  fi
  echo "$body"
}

fetch_break_rows() {
  local recon_id="$1" run_id="$2" size="$3"
  curl -sS -G "$BASE_URL/api/reconciliations/$recon_id/results" \
    -H "Authorization: Bearer $MAKER_TOKEN" \
    --data-urlencode "runId=$run_id" \
    --data-urlencode "size=$size" \
    --data-urlencode "includeTotals=false"
}

post_break_comment() {
  local break_id="$1" text="$2" action="$3"
  local response
  response=$(curl -sS -w '\n%{http_code}' -X POST \
    "$BASE_URL/api/breaks/$break_id/comments" \
    -H "Authorization: Bearer $MAKER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"comment\":\"$text\",\"action\":\"$action\"}")
  local status="${response##*$'\n'}"
  if [[ "$status" != "200" ]]; then
    fail "Failed to add comment for break $break_id"
  fi
}

bulk_update_breaks() {
  local break_ids_json="$1" status="$2" comment="$3" action="$4" correlation="$5"
  local response
  response=$(curl -sS -w '\n%{http_code}' -X POST "$BASE_URL/api/breaks/bulk" \
    -H "Authorization: Bearer $MAKER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"breakIds\":$break_ids_json,\"status\":$status,\"comment\":\"$comment\",\"action\":\"$action\",\"correlationId\":\"$correlation\"}")
  local status_code="${response##*$'\n'}"
  if [[ "$status_code" != "200" ]]; then
    fail "Bulk update failed (HTTP $status_code)."
  fi
}

queue_export() {
  local recon_id="$1" label="$2"
  if [[ "$REPORT_FORMAT" == "NONE" ]]; then
    return
  fi
  local payload
  payload=$(jq -n --arg format "$REPORT_FORMAT" --arg prefix "$label" '{format: $format, filters: {}, fileNamePrefix: $prefix, includeMetadata: false}')
  local response
  response=$(curl -sS -w '\n%{http_code}' -X POST "$BASE_URL/api/reconciliations/$recon_id/export-jobs" \
    -H "Authorization: Bearer $MAKER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "$payload")
  local body="${response%$'\n'*}"
  local status="${response##*$'\n'}"
  if [[ "$status" != "201" && "$status" != "202" ]]; then
    fail "Failed to queue export (HTTP $status): $body"
  fi
  local job_id
  job_id=$(echo "$body" | jq -r '.id')
  if [[ "$job_id" == "null" ]]; then
    fail "Unable to determine export job id."
  fi
  local attempt=0
  while (( attempt < POLL_ATTEMPTS )); do
    local job
    job=$(curl -sS -H "Authorization: Bearer $MAKER_TOKEN" "$BASE_URL/api/export-jobs/$job_id")
    local status_value
    status_value=$(echo "$job" | jq -r '.status')
    if [[ "$status_value" == "COMPLETED" ]]; then
      return
    fi
    if [[ "$status_value" == "FAILED" ]]; then
      fail "Export job $job_id failed: $(echo "$job" | jq -r '.errorMessage // "unknown"')"
    fi
    sleep "$POLL_DELAY"
    ((attempt++))
  done
  fail "Export job $job_id did not complete within expected time."
}

source_config_value() {
  local code="$1" key="$2"
  local map_key="${code},${key}"
  echo "${SOURCE_CONFIG[$map_key]:-}"
}

source_media_type() {
  local value
  value=$(source_config_value "$1" "media_type")
  if [[ -n "$value" ]]; then
    echo "$value"
  else
    echo 'text/csv'
  fi
}

source_adapter_type() {
  local value
  value=$(source_config_value "$1" "adapter_type")
  if [[ -n "$value" ]]; then
    echo "$value"
  else
    echo 'CSV_FILE'
  fi
}

source_options_json() {
  source_config_value "$1" "options_json"
}

source_fixture_path() {
  source_config_value "$1" "fixture_path"
}

seed_global_multi_asset_history() {
  local payload="$1" recon_id="$2" code="$3"
  local source_codes=(GLOBAL_MASTER APAC_MULTI EMEA_MULTI AMERICAS_CASH DERIVATIVES_FEED GLOBAL_CUSTODY)

  for source_code in "${source_codes[@]}"; do
    local file_path
    file_path=$(source_fixture_path "$source_code")
    if [[ -z "$file_path" || ! -f "$file_path" ]]; then
      fail "Fixture for $source_code not found at $file_path"
    fi
  done

  for (( day_offset=0; day_offset < DAYS; day_offset++ )); do
    run_date=$(iso_date_utc "$day_offset")
    for (( run_index=1; run_index <= RUNS_PER_DAY; run_index++ )); do
      run_key="${code}-${run_date}-r${run_index}"
      local label="${run_key//_/ -}"

      for source_code in "${source_codes[@]}"; do
        local file_path
        file_path=$(source_fixture_path "$source_code")
        local adapter_type
        adapter_type=$(source_adapter_type "$source_code")
        local media_type
        media_type=$(source_media_type "$source_code")
        local options_json
        options_json=$(source_options_json "$source_code")
        upload_batch_with_metadata \
          "$recon_id" \
          "$source_code" \
          "$file_path" \
          "$label-$source_code" \
          "$adapter_type" \
          "$options_json" \
          "$media_type"
      done

      run_comment="Historic run $run_key"
      run_detail=$(trigger_run "$recon_id" "$run_comment" "$MAKER_USERNAME")
      run_id=$(echo "$run_detail" | jq -r '.summary.runId')
      if [[ "$run_id" == "null" ]]; then
        fail "Unable to determine run id for $run_key"
      fi

      if (( day_offset >= 1 )); then
        apply_post_run_actions "$recon_id" "$run_id" "$run_key" "400"
      else
        queue_export "$recon_id" "fresh-$run_key"
      fi

      if [[ "$CI_MODE" != "true" ]]; then
        sleep 1
      fi
    done
  done

  log "Completed seeding for $code."
}

apply_post_run_actions() {
  local recon_id="$1" run_id="$2" run_key="$3" record_limit="$4"
  local attempt=0
  local rows_json
  while (( attempt < POLL_ATTEMPTS )); do
    rows_json=$(fetch_break_rows "$recon_id" "$run_id" "$record_limit")
    local count
    count=$(echo "$rows_json" | jq '.rows | length')
    if [[ "$count" != "0" ]]; then
      break
    fi
    sleep "$POLL_DELAY"
    ((attempt++))
  done

  local break_ids_json
  break_ids_json=$(echo "$rows_json" | jq '[.rows[].breakId]')
  local break_count
  break_count=$(echo "$break_ids_json" | jq 'length')
  if (( break_count == 0 )); then
    log "Run $run_id yielded no breaks; skipping maker-checker actions."
    return
  fi

  # Add comments for each break.
  local comment_text="Auto seeded historic review" action="AUTO_NOTE"
  for break_id in $(echo "$rows_json" | jq -r '.rows[].breakId'); do
    post_break_comment "$break_id" "$comment_text" "$action"
  done

  local correlation="seed-$run_key"
  bulk_update_breaks "$break_ids_json" "\"PENDING_APPROVAL\"" "Submitting for checker approval" "MAKER_REVIEW" "$correlation"
  bulk_update_breaks "$break_ids_json" "\"CLOSED\"" "Checker approval completed" "CHECKER_SIGNOFF" "$correlation"
  queue_export "$recon_id" "historic-$run_key"
}

record_limit_for_fetch() {
  local base="$1"
  if (( base < 200 )); then
    echo 200
  else
    echo $((base * 2))
  fi
}

for payload in "${PAYLOAD_FILES[@]}"; do
  code=$(jq -r '.code' "$payload")
  recon_id=$(ensure_reconciliation "$payload")
  log "Recon $code ready (id=$recon_id)."

  if [[ "$code" == "GLOBAL_MULTI_ASSET_HISTORY" ]]; then
    seed_global_multi_asset_history "$payload" "$recon_id" "$code"
    continue
  fi

  for (( day_offset=0; day_offset < DAYS; day_offset++ )); do
    run_date=$(iso_date_utc "$day_offset")
    for (( run_index=1; run_index <= RUNS_PER_DAY; run_index++ )); do
      run_key="${code}-${run_date}-r${run_index}"
      record_count=$(random_record_count)
      anchor_file="$TMP_ROOT/${run_key}-anchor.csv"
      compare_file="$TMP_ROOT/${run_key}-compare.csv"
      python3 "$GENERATOR" "$anchor_file" "$compare_file" "$record_count" "$run_date" "$run_key"

      label="${run_key//_/ -}"
      anchor_source=$(jq -r '.sources[] | select(.anchor == true) | .code' "$payload")
      compare_source=$(jq -r '.sources[] | select(.anchor == false) | .code' "$payload")
      upload_batch "$recon_id" "$anchor_source" "$anchor_file" "$label-anchor"
      upload_batch "$recon_id" "$compare_source" "$compare_file" "$label-compare"
      rm -f "$anchor_file" "$compare_file"

      run_comment="Historic run $run_key"
      run_detail=$(trigger_run "$recon_id" "$run_comment" "$MAKER_USERNAME")
      run_id=$(echo "$run_detail" | jq -r '.summary.runId')
      if [[ "$run_id" == "null" ]]; then
        fail "Unable to determine run id for $run_key"
      fi

      if (( day_offset >= 1 )); then
        limit=$(record_limit_for_fetch "$record_count")
        apply_post_run_actions "$recon_id" "$run_id" "$run_key" "$limit"
      else
        queue_export "$recon_id" "fresh-$run_key"
      fi

      if [[ "$CI_MODE" != "true" ]]; then
        sleep 1
      fi
    done
  done
  log "Completed seeding for $code."

done

log "Historical seeding complete."
