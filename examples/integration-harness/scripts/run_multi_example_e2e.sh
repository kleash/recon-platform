#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
COMMON_DIR="$PROJECT_ROOT/examples/common"
CASH_DIR="$PROJECT_ROOT/examples/cash-vs-gl"
CUSTODIAN_DIR="$PROJECT_ROOT/examples/custodian-trade"
HARNESS_DIR="$PROJECT_ROOT/examples/integration-harness"
APP_PORT="${APP_PORT:-8080}"
JWT_SECRET_VALUE="MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
LOG_DIR="$(mktemp -d)"
APP_LOG="$LOG_DIR/harness.log"
APP_PID=""
LAST_RESPONSE=""

cleanup() {
    if [[ -n "$APP_PID" ]]; then
        if kill -0 "$APP_PID" >/dev/null 2>&1; then
            kill "$APP_PID" >/dev/null 2>&1 || true
            wait "$APP_PID" 2>/dev/null || true
        fi
    fi
    rm -rf "$LOG_DIR"
}
trap cleanup EXIT INT TERM

log() {
    printf '[%(%H:%M:%S)T] %s\n' -1 "$1"
}

log "Building backend platform artifact"
"$BACKEND_DIR/mvnw" -f "$BACKEND_DIR/pom.xml" clean install -DskipTests -Dspring-boot.repackage.skip=true >/dev/null

log "Installing shared example support library"
"$BACKEND_DIR/mvnw" -f "$COMMON_DIR/pom.xml" clean install -DskipTests >/dev/null

log "Installing cash vs GL example module"
"$BACKEND_DIR/mvnw" -f "$CASH_DIR/pom.xml" clean install -DskipTests >/dev/null

log "Installing custodian trade example module"
"$BACKEND_DIR/mvnw" -f "$CUSTODIAN_DIR/pom.xml" clean install -DskipTests >/dev/null

log "Packaging multi-example harness application"
"$BACKEND_DIR/mvnw" -f "$HARNESS_DIR/pom.xml" clean package -DskipTests >/dev/null

HARNESS_JAR="$HARNESS_DIR/target/multi-example-harness-0.1.0.jar"
if [[ ! -f "$HARNESS_JAR" ]]; then
    echo "Harness jar not found at $HARNESS_JAR" >&2
    exit 1
fi

log "Starting harness application (logs: $APP_LOG)"
(
    cd "$HARNESS_DIR"
    JWT_SECRET="$JWT_SECRET_VALUE" \
    SPRING_PROFILES_ACTIVE="example-harness" \
    SERVER_PORT="$APP_PORT" \
    java -jar "$HARNESS_JAR" >"$APP_LOG" 2>&1
) &
APP_PID=$!

log "Waiting for application health endpoint"
STARTED=false
for _ in {1..60}; do
    if curl -sf "http://localhost:$APP_PORT/actuator/health" >/dev/null; then
        STATUS=$(curl -sf "http://localhost:$APP_PORT/actuator/health")
        if python - "$STATUS" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
if payload.get("status") == "UP":
    sys.exit(0)
sys.exit(1)
PY
        then
            STARTED=true
            break
        fi
    fi
    sleep 2
    if ! kill -0 "$APP_PID" >/dev/null 2>&1; then
        break
    fi
done

if [[ "$STARTED" != true ]]; then
    echo "Application failed to start. Tail of log:" >&2
    tail -n 50 "$APP_LOG" >&2
    exit 1
fi

log "Authenticating with platform"
authenticate() {
    local attempts=0
    local response
    local token
    while (( attempts < 30 )); do
        if response=$(curl -sSf -X POST "http://localhost:$APP_PORT/api/auth/login" \
            -H 'Content-Type: application/json' \
            -d '{"username":"ops1","password":"password"}') && [[ -n "$response" ]]; then
            if token=$(python - "$response" <<'PY'
import json,sys
try:
    payload=json.loads(sys.argv[1])
except json.JSONDecodeError:
    raise SystemExit(2)
token=payload.get("token")
if not token:
    raise SystemExit(3)
print(token)
PY
); then
                printf '%s' "$token"
                return 0
            fi
        fi
        attempts=$((attempts + 1))
        sleep 2
    done
    return 1
}

if ! TOKEN=$(authenticate); then
    log "Failed to authenticate with platform after multiple attempts"
    tail -n 50 "$APP_LOG" >&2 || true
    exit 1
fi

AUTH_HEADER=("Authorization: Bearer $TOKEN")

log "Discovering reconciliations seeded by ETL pipelines"
discover_reconciliations() {
    local attempts=0
    local payload=""
    local ids=""
    while (( attempts < 30 )); do
        if payload=$(curl -sSf -H "${AUTH_HEADER[@]}" "http://localhost:$APP_PORT/api/reconciliations" 2>/dev/null); then
            LAST_RESPONSE="$payload"
            if ids=$(python - "$payload" <<'PY'
import json,sys
text=sys.argv[1] or '[]'
recons=json.loads(text)
code_to_id={item['code']: item['id'] for item in recons}
required=["CASH_VS_GL_SIMPLE","CUSTODIAN_TRADE_COMPLEX"]
missing=[code for code in required if code not in code_to_id]
if missing:
    raise SystemExit(1)
print(code_to_id["CASH_VS_GL_SIMPLE"])
print(code_to_id["CUSTODIAN_TRADE_COMPLEX"])
PY
); then
                printf '%s' "$ids"
                return 0
            fi
        fi
        attempts=$((attempts + 1))
        sleep 2
    done
    LAST_RESPONSE="$payload"
    return 1
}

if ! mapfile -t RECON_IDS < <(discover_reconciliations); then
    log "Failed to discover seeded reconciliations"
    printf 'Last response: %s\n' "${LAST_RESPONSE:-<empty>}" >&2
    tail -n 50 "$APP_LOG" >&2 || true
    exit 1
fi
CASH_ID="${RECON_IDS[0]}"
CUSTODIAN_ID="${RECON_IDS[1]}"

log "Executing initial cash vs GL reconciliation run"
CASH_TRIGGER_PAYLOAD='{"triggerType":"MANUAL_API","correlationId":"harness-initial","comments":"Initial harness verification","initiatedBy":"integration-harness"}'
if ! CASH_TRIGGER_RESULT=$(curl -sSf -X POST "http://localhost:$APP_PORT/api/reconciliations/$CASH_ID/run" \
    -H 'Content-Type: application/json' -H "${AUTH_HEADER[@]}" -d "$CASH_TRIGGER_PAYLOAD" 2>/dev/null); then
    log "Unable to trigger cash vs GL reconciliation run"
    tail -n 50 "$APP_LOG" >&2 || true
    exit 1
fi

log "Validating cash vs GL data ingestion"
wait_for_cash_run() {
    local recon_id="$1"
    local attempts=0
    local payload=""
    while (( attempts < 30 )); do
        if payload=$(curl -sSf -H "${AUTH_HEADER[@]}" "http://localhost:$APP_PORT/api/reconciliations/$recon_id/runs/latest" 2>/dev/null); then
            LAST_RESPONSE="$payload"
            if python - "$payload" <<'PY'
import json,sys
run=json.loads(sys.argv[1] or '{}')
summary=run.get('summary') or {}
matched=summary.get('matched', 0)
mismatched=summary.get('mismatched', 0)
missing=summary.get('missing', 0)
if matched > 0 and mismatched > 0 and missing > 0:
    sys.exit(0)
sys.exit(1)
PY
then
                return 0
            fi
        fi
        attempts=$((attempts + 1))
        sleep 2
    done
    LAST_RESPONSE="$payload"
    return 1
}

if ! wait_for_cash_run "$CASH_ID"; then
    log "Cash vs GL latest run did not contain expected activity"
    printf 'Last response: %s\n' "${LAST_RESPONSE:-<empty>}" >&2
    tail -n 50 "$APP_LOG" >&2 || true
    exit 1
fi

log "Inspecting custodian cutoffs for automatic triggers"
wait_for_cutoffs() {
    local attempts=0
    local payload=""
    while (( attempts < 30 )); do
        if payload=$(curl -sSf -H "${AUTH_HEADER[@]}" "http://localhost:$APP_PORT/api/examples/custodian/cutoffs" 2>/dev/null); then
            LAST_RESPONSE="$payload"
            if python - "$payload" <<'PY'
import json,sys
cutoffs=json.loads(sys.argv[1] or '[]')
auto=[c for c in cutoffs if c.get('cycle') == 'MORNING' and not c.get('triggeredByCutoff')]
cutoff=[c for c in cutoffs if c.get('cycle') == 'EVENING' and c.get('triggeredByCutoff')]
if not auto or not cutoff:
    sys.exit(1)
auto_comment=(auto[0].get('runDetail', {}).get('summary', {}).get('triggerComments', '') or '').lower()
cutoff_comment=(cutoff[0].get('runDetail', {}).get('summary', {}).get('triggerComments', '') or '').lower()
if 'automatic' not in auto_comment:
    sys.exit(1)
if 'cutoff' not in cutoff_comment:
    sys.exit(1)
sys.exit(0)
PY
then
                return 0
            fi
        fi
        attempts=$((attempts + 1))
        sleep 2
    done
    LAST_RESPONSE="$payload"
    return 1
}

if ! wait_for_cutoffs; then
    log "Custodian cutoffs did not report expected triggers"
    printf 'Last response: %s\n' "${LAST_RESPONSE:-<empty>}" >&2
    tail -n 50 "$APP_LOG" >&2 || true
    exit 1
fi

log "Ensuring scheduled reports executed"
wait_for_reports() {
    local attempts=0
    local payload=""
    while (( attempts < 30 )); do
        if payload=$(curl -sSf -H "${AUTH_HEADER[@]}" "http://localhost:$APP_PORT/api/examples/custodian/reports" 2>/dev/null); then
            LAST_RESPONSE="$payload"
            if python - "$payload" <<'PY'
import json,sys
reports=json.loads(sys.argv[1] or '[]')
if len(reports) != 3:
    sys.exit(1)
if not all(r.get('workbook') for r in reports):
    sys.exit(1)
sys.exit(0)
PY
then
                return 0
            fi
        fi
        attempts=$((attempts + 1))
        sleep 2
    done
    LAST_RESPONSE="$payload"
    return 1
}

if ! wait_for_reports; then
    log "Scheduled reports were not generated as expected"
    printf 'Last response: %s\n' "${LAST_RESPONSE:-<empty>}" >&2
    tail -n 50 "$APP_LOG" >&2 || true
    exit 1
fi

log "Triggering manual reconciliation due to missing evening file"
MANUAL_PAYLOAD='{"triggerType":"MANUAL_API","correlationId":"harness-manual","comments":"Manual trigger for missing custodial file","initiatedBy":"integration-harness"}'
MANUAL_RUN=$(curl -sSf -X POST "http://localhost:$APP_PORT/api/reconciliations/$CUSTODIAN_ID/run" \
    -H 'Content-Type: application/json' -H "${AUTH_HEADER[@]}" -d "$MANUAL_PAYLOAD" 2>/dev/null)
if ! python - "$MANUAL_RUN" <<'PY'
import json,sys
run=json.loads(sys.argv[1] or '{}')
summary=run.get('summary') or {}
if summary.get('triggerType') != 'MANUAL_API':
    sys.exit(1)
if 'Manual trigger for missing custodial file' not in (summary.get('triggerComments', '') or ''):
    sys.exit(1)
sys.exit(0)
PY
then
    log "Manual trigger validation failed"
    printf 'Manual run response: %s\n' "${MANUAL_RUN:-<empty>}" >&2
    tail -n 50 "$APP_LOG" >&2 || true
    exit 1
fi

log "Manual trigger appended to run history"
UPDATED_RUNS=$(curl -sSf -H "${AUTH_HEADER[@]}" "http://localhost:$APP_PORT/api/reconciliations/$CUSTODIAN_ID/runs?limit=1" 2>/dev/null)
if ! python - "$UPDATED_RUNS" <<'PY'
import json,sys
runs=json.loads(sys.argv[1] or '[]')
if not runs:
    sys.exit(1)
latest=runs[0]
if latest.get('triggerType') != 'MANUAL_API':
    sys.exit(1)
if latest.get('triggerComments') != 'Manual trigger for missing custodial file':
    sys.exit(1)
sys.exit(0)
PY
then
    log "Run history validation failed"
    printf 'Run history payload: %s\n' "${UPDATED_RUNS:-<empty>}" >&2
    tail -n 50 "$APP_LOG" >&2 || true
    exit 1
fi

log "Integration harness validation completed successfully"
