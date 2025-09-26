#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
HARNESS_DIR="$PROJECT_ROOT/examples/integration-harness"
COMMON_DIR="$PROJECT_ROOT/examples/common"
APP_PORT="${APP_PORT:-8080}"
BASE_URL="http://localhost:${APP_PORT}"
JWT_SECRET_VALUE="MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
LOG_DIR="$(mktemp -d)"
APP_LOG="$LOG_DIR/platform.log"
APP_PID=""

PAYLOAD_DIR="$HARNESS_DIR/payloads"
PAYLOADS=(
  "$PAYLOAD_DIR/cash-vs-gl.json"
  "$PAYLOAD_DIR/custodian-trade.json"
  "$PAYLOAD_DIR/securities-position.json"
)

ADMIN_USERNAME="admin1"
ADMIN_PASSWORD="password"
OPS_USERNAME="ops1"
OPS_PASSWORD="password"

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

require_binary() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Required dependency '$1' is not installed" >&2
        exit 1
    fi
}

require_binary curl
require_binary jq
require_binary lsof

ensure_port_available() {
    if lsof -nPi :"$APP_PORT" >/dev/null 2>&1; then
        echo "Port $APP_PORT is already in use. Stop the existing process or set APP_PORT to a free port." >&2
        exit 1
    fi
}

log() {
    printf '[%s] %s\n' "$(date +%H:%M:%S)" "$1"
}

build_artifacts() {
    log "Building backend platform artifact"
    "$BACKEND_DIR/mvnw" -f "$BACKEND_DIR/pom.xml" clean install -DskipTests -Dspring-boot.repackage.skip=true >/dev/null

    log "Installing shared example support library"
    "$BACKEND_DIR/mvnw" -f "$COMMON_DIR/pom.xml" clean install -DskipTests >/dev/null

    log "Packaging integration harness application and ingestion CLI"
    "$BACKEND_DIR/mvnw" -f "$HARNESS_DIR/pom.xml" package -DskipTests >/dev/null

    local assembly_jar="$HARNESS_DIR/target/multi-example-harness-0.1.0-jar-with-dependencies.jar"
    if [[ -f "$assembly_jar" ]]; then
        cp "$assembly_jar" "$HARNESS_DIR/target/integration-ingestion-cli.jar"
    fi
}

start_platform() {
    local harness_jar="$HARNESS_DIR/target/multi-example-harness-0.1.0-exec.jar"
    if [[ ! -f "$harness_jar" ]]; then
        echo "Harness jar not found at $harness_jar" >&2
        exit 1
    fi

    log "Starting platform (logs: $APP_LOG)"
    (
        cd "$HARNESS_DIR"
        JWT_SECRET="$JWT_SECRET_VALUE" \
        SPRING_PROFILES_ACTIVE="example-harness" \
        SERVER_PORT="$APP_PORT" \
        java -jar "$harness_jar" >"$APP_LOG" 2>&1
    ) &
    APP_PID=$!
}

wait_for_health() {
    log "Waiting for platform health endpoint"
    local attempts=0
    while (( attempts < 60 )); do
        if curl -sSf "$BASE_URL/actuator/health" >/dev/null; then
            local status
            status=$(curl -sSf "$BASE_URL/actuator/health") || true
            if [[ "$(echo "$status" | jq -r '.status')" == "UP" ]]; then
                log "Platform reported healthy"
                return 0
            fi
        fi
        sleep 2
        ((attempts++))
        if [[ -n "$APP_PID" ]]; then
            if ! kill -0 "$APP_PID" >/dev/null 2>&1; then
                tail -n 50 "$APP_LOG" >&2 || true
                echo "Platform process terminated while waiting for health" >&2
                exit 1
            fi
        fi
    done
    echo "Platform did not become healthy within timeout" >&2
    tail -n 50 "$APP_LOG" >&2 || true
    exit 1
}

login() {
    local username="$1"
    local password="$2"
    local attempts=0
    while (( attempts < 20 )); do
        local response
        if response=$(curl -sS -X POST "$BASE_URL/api/auth/login" \
            -H 'Content-Type: application/json' \
            -d "{\"username\":\"$username\",\"password\":\"$password\"}"); then
            local token
            token=$(echo "$response" | jq -r '.token // empty')
            if [[ -n "$token" ]]; then
                printf '%s' "$token"
                return 0
            fi
        fi
        sleep 1
        ((attempts++))
    done
    return 1
}

upsert_reconciliation() {
    local payload_path="$1"
    local token="$2"
    local code
    code=$(jq -r '.code' "$payload_path")
    if [[ -z "$code" || "$code" == "null" ]]; then
        echo "Payload $payload_path missing reconciliation code" >&2
        exit 1
    fi

    local existing
    existing=$(curl -sS -H "Authorization: Bearer $token" \
        "$BASE_URL/api/admin/reconciliations?search=$code&size=10")
    local recon_id
    recon_id=$(echo "$existing" | jq -r ".items[] | select(.code == \"$code\") | .id" | head -n 1)

    local method url expected_status
    if [[ -n "$recon_id" && "$recon_id" != "null" ]]; then
        method="PUT"
        url="$BASE_URL/api/admin/reconciliations/$recon_id"
        expected_status=200
    else
        method="POST"
        url="$BASE_URL/api/admin/reconciliations"
        expected_status=201
    fi

    local response status
    response=$(mktemp)
    status=$(curl -sS -o "$response" -w '%{http_code}' -X "$method" "$url" \
        -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' \
        -d @"$payload_path")
    if [[ "$status" -ne "$expected_status" ]]; then
        echo "Failed to submit reconciliation payload for $code (status $status)" >&2
        cat "$response" >&2 || true
        rm -f "$response"
        exit 1
    fi

    if [[ "$method" == "POST" ]]; then
        recon_id=$(jq -r '.id' "$response")
    fi
    rm -f "$response"

    if [[ -z "$recon_id" || "$recon_id" == "null" ]]; then
        echo "Unable to determine reconciliation id for $code" >&2
        exit 1
    fi

    printf '%s\n' "$recon_id"
}

trigger_run() {
    local recon_id="$1"
    local token="$2"
    local comments="$3"
    local response status tmp
    tmp=$(mktemp)
    status=$(curl -sS -o "$tmp" -w '%{http_code}' -X POST "$BASE_URL/api/reconciliations/$recon_id/run" \
        -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' \
        -d "{\"triggerType\":\"MANUAL_API\",\"comments\":\"$comments\",\"initiatedBy\":\"integration-harness\"}")
    if [[ "$status" -ne 200 ]]; then
        echo "Trigger run request failed for reconciliation $recon_id (status $status)" >&2
        cat "$tmp" >&2 || true
        rm -f "$tmp"
        exit 1
    fi
    cat "$tmp"
    rm -f "$tmp"
}

validate_summary() {
    local payload="$1"
    local code="$2"
    local expr
    case "$code" in
        CASH_VS_GL_SIMPLE)
            expr='(.summary.matched // 0) > 0 and (.summary.mismatched // 0) > 0 and (.summary.missing // 0) > 0'
            ;;
        CUSTODIAN_TRADE_COMPLEX)
            expr='(.summary.missing // 0) > 0'
            ;;
        SEC_POSITION_COMPLEX)
            expr='(.summary.matched // 0) > 0 and (.summary.mismatched // 0) > 0'
            ;;
        *)
            expr='true'
            ;;
    esac

    if ! echo "$payload" | jq -e "$expr" >/dev/null; then
        echo "Run summary validation failed for $code" >&2
        echo "$payload" | jq '.' >&2 || true
        exit 1
    fi
}

main() {
    ensure_port_available
    build_artifacts
    start_platform
    wait_for_health

    log "Authenticating admin user"
    if ! ADMIN_TOKEN=$(login "$ADMIN_USERNAME" "$ADMIN_PASSWORD"); then
        echo "Failed to authenticate admin user" >&2
        exit 1
    fi

    local -a CODES=()
    local -a IDS=()
    for payload in "${PAYLOADS[@]}"; do
        log "Installing reconciliation from $(basename "$payload")"
        recon_id=$(upsert_reconciliation "$payload" "$ADMIN_TOKEN")
        code=$(jq -r '.code' "$payload")
        CODES+=("$code")
        IDS+=("$recon_id")
    done

    local cli_jar="$HARNESS_DIR/target/integration-ingestion-cli.jar"
    if [[ ! -f "$cli_jar" ]]; then
        echo "Ingestion CLI jar not found at $cli_jar" >&2
        exit 1
    fi

    log "Running ingestion CLI to load all scenarios"
    java -jar "$cli_jar" \
        --base-url "$BASE_URL" \
        --username "$ADMIN_USERNAME" \
        --password "$ADMIN_PASSWORD" \
        --scenario all

    log "Authenticating operations user"
    if ! OPS_TOKEN=$(login "$OPS_USERNAME" "$OPS_PASSWORD"); then
        echo "Failed to authenticate operations user" >&2
        exit 1
    fi

    for idx in "${!CODES[@]}"; do
        code="${CODES[$idx]}"
        recon_id="${IDS[$idx]}"
        log "Triggering reconciliation run for $code"
        run_payload=$(trigger_run "$recon_id" "$OPS_TOKEN" "Harness validation")
        summary_json=$(echo "$run_payload" | jq -c '.summary // empty')
        if [[ -z "$summary_json" ]]; then
            log "Run payload for $code: $(echo "$run_payload" | jq -c '.')"
        fi
        log "Run summary for $code: ${summary_json:-<unavailable>}"
        validate_summary "$run_payload" "$code"
    done

    log "Integration harness validation completed successfully"
}

main "$@"
