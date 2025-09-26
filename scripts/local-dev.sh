#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.yml"
COMPOSE_CMD=()
BACKEND_PID=""
FRONTEND_PID=""

usage() {
  cat <<'USAGE'
Usage: local-dev.sh <command> [options]

Commands:
  bootstrap              Install backend and frontend dependencies.
  infra <subcommand>     Manage local infrastructure (requires Docker).
                         Subcommands: start, stop, status, logs [service], clean
  backend [options]      Run the Spring Boot backend (default profile: dev).
                         Options: --profile <name>
  frontend               Run the Angular frontend dev server.
  all [options]          Bootstrap deps, start infra, backend, and frontend together.
                         Options: --profile <name> --skip-infra --skip-bootstrap
  seed                   Apply sample reconciliations, ingest fixtures, and trigger runs against a running platform.
  stop                   Stop backend/frontend processes started by this script and tear down local infrastructure.

Examples:
  ./scripts/local-dev.sh bootstrap
  ./scripts/local-dev.sh infra start
  ./scripts/local-dev.sh backend --profile local-mariadb
  ./scripts/local-dev.sh all --profile local-mariadb
  ./scripts/local-dev.sh seed
  ./scripts/local-dev.sh stop
USAGE
}

require_command() {
  local cmd="$1"
  local help_msg="${2:-Install the prerequisite and retry.}"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n%s\n' "$cmd" "$help_msg" >&2
    exit 1
  fi
}

ensure_compose_cmd() {
  if [[ ${#COMPOSE_CMD[@]} -gt 0 ]]; then
    return
  fi

  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD=(docker-compose)
  else
    printf 'Docker Compose is required for infra commands. Install Docker Desktop or docker-compose.\n' >&2
    exit 1
  fi
}

docker_compose() {
  ensure_compose_cmd
  if [[ ! -f "$COMPOSE_FILE" ]]; then
    printf 'Docker Compose file not found at %s\n' "$COMPOSE_FILE" >&2
    exit 1
  fi
  "${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" "$@"
}

bootstrap_deps() {
  require_command java "Install JDK 17+ and ensure 'java' is on your PATH."
  require_command node "Install Node.js 18+ and ensure 'node' is on your PATH."
  require_command npm "Install npm 9+ (bundled with Node.js) and ensure it is on your PATH."

  printf 'Bootstrapping backend dependencies...\n'
  (cd "$ROOT_DIR/backend" && ./mvnw dependency:go-offline)

  printf 'Bootstrapping frontend dependencies...\n'
  (cd "$ROOT_DIR/frontend" && npm install)
}

infra_start() {
  printf 'Starting local infrastructure...\n'
  docker_compose up -d
  printf 'MariaDB is starting on port %s (override with RECON_DB_PORT).\n' "${RECON_DB_PORT:-3306}"
  printf 'LDAP is starting on port %s (override with RECON_LDAP_PORT).\n' "${RECON_LDAP_PORT:-389}"
}

infra_stop() {
  printf 'Stopping local infrastructure...\n'
  docker_compose down
}

infra_status() {
  docker_compose ps
}

infra_logs() {
  docker_compose logs -f "$@"
}

infra_clean() {
  printf 'Stopping infrastructure and removing volumes...\n'
  docker_compose down -v
}

run_backend() {
  local profile="dev"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --profile)
        shift
        profile="${1:-}"
        if [[ -z "$profile" ]]; then
          printf 'Missing value for --profile.\n' >&2
          exit 1
        fi
        ;;
      *)
        printf 'Unknown backend option: %s\n' "$1" >&2
        usage
        exit 1
        ;;
    esac
    shift
  done

  printf 'Starting backend with profile "%s"...\n' "$profile"
  (cd "$ROOT_DIR/backend" && ./mvnw spring-boot:run -Dspring-boot.run.profiles="$profile")
}

run_frontend() {
  printf 'Starting frontend dev server...\n'
  (cd "$ROOT_DIR/frontend" && npm start)
}

run_backend_background() {
  run_backend "$@" &
  BACKEND_PID=$!
}

run_frontend_background() {
  run_frontend &
  FRONTEND_PID=$!
}

cleanup_background_processes() {
  for pid in "$BACKEND_PID" "$FRONTEND_PID"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
}

find_process_pid() {
  local pattern="$1"
  pgrep -f "$pattern" | head -n 1 || true
}

stop_backend() {
  local pid
  pid=$(find_process_pid "UniversalReconciliationPlatformApplication")
  if [[ -n "$pid" ]]; then
    printf 'Stopping backend process (pid %s)...\n' "$pid"
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
    if kill -0 "$pid" >/dev/null 2>&1; then
      printf 'Backend still running, sending SIGKILL...\n'
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
  fi
}

stop_frontend() {
  local pid
  pid=$(find_process_pid "ng serve")
  if [[ -n "$pid" ]]; then
    printf 'Stopping frontend dev server (pid %s)...\n' "$pid"
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
    if kill -0 "$pid" >/dev/null 2>&1; then
      printf 'Frontend still running, sending SIGKILL...\n'
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
  fi
}

stop_all() {
  stop_backend
  stop_frontend
  if docker ps --format '{{.Names}}' | grep -q '^urp_'; then
    printf 'Stopping local infrastructure...\n'
    docker_compose down >/dev/null 2>&1 || true
  fi
  printf 'All local services stopped.\n'
}

run_all() {
  local profile="dev"
  local skip_infra=false
  local skip_bootstrap=false

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --profile)
        shift
        profile="${1:-}"
        if [[ -z "$profile" ]]; then
          printf 'Missing value for --profile.\n' >&2
          exit 1
        fi
        ;;
      --skip-infra)
        skip_infra=true
        ;;
      --skip-bootstrap)
        skip_bootstrap=true
        ;;
      *)
        printf 'Unknown option for all command: %s\n' "$1" >&2
        usage
        exit 1
        ;;
    esac
    shift
  done

  if ! $skip_bootstrap; then
    bootstrap_deps
  fi

  if ! $skip_infra; then
    infra_start
  fi

  trap cleanup_background_processes EXIT INT TERM

  run_backend_background --profile "$profile"
  run_frontend_background

  wait
}

require_http_tools() {
  require_command curl "Install curl to interact with the platform API."
  require_command jq "Install jq to parse JSON API responses."
}

login_user() {
  local base_url="$1"
  local username="$2"
  local password="$3"

  local response status tmp
  tmp=$(mktemp)
  status=$(curl -sS -o "$tmp" -w '%{http_code}' \
    -X POST "$base_url/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\"}")

  if [[ "$status" -ne 200 ]]; then
    printf 'Authentication failed for user %s (status %s). Response:%s' "$username" "$status" $'\n' >&2
    cat "$tmp" >&2 || true
    rm -f "$tmp"
    return 1
  fi

  local token
  token=$(jq -r '.token // empty' "$tmp")
  rm -f "$tmp"

  if [[ -z "$token" ]]; then
    printf 'Authentication response for %s did not include a token.\n' "$username" >&2
    return 1
  fi

  printf '%s' "$token"
}

upsert_reconciliation_payload() {
  local base_url="$1"
  local payload_path="$2"
  local token="$3"

  local code
  code=$(jq -r '.code // empty' "$payload_path")
  if [[ -z "$code" ]]; then
    printf 'Payload %s is missing a reconciliation code.\n' "$payload_path" >&2
    return 1
  fi

  local query_response
  query_response=$(curl -sS -G "$base_url/api/admin/reconciliations" \
    -H "Authorization: Bearer $token" \
    --data-urlencode "search=$code" \
    --data-urlencode "size=50")

  local recon_id
  recon_id=$(echo "$query_response" | jq -r ".items[] | select(.code == \"$code\") | .id" | head -n 1)

  local method url expected_status
  if [[ -n "$recon_id" && "$recon_id" != "null" ]]; then
    method="PUT"
    url="$base_url/api/admin/reconciliations/$recon_id"
    expected_status=200
  else
    method="POST"
    url="$base_url/api/admin/reconciliations"
    expected_status=201
  fi

  local tmp status
  tmp=$(mktemp)
  status=$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" "$url" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    --data @"$payload_path")

  if [[ "$status" -ne "$expected_status" ]]; then
    printf 'Failed to submit reconciliation payload for %s (status %s).\n' "$code" "$status" >&2
    cat "$tmp" >&2 || true
    rm -f "$tmp"
    return 1
  fi

  recon_id=$(jq -r '.id // empty' "$tmp")
  rm -f "$tmp"

  if [[ -z "$recon_id" ]]; then
    printf 'Unable to determine reconciliation id after submitting payload %s.\n' "$payload_path" >&2
    return 1
  fi

  printf '%s' "$recon_id"
}

trigger_manual_run() {
  local base_url="$1"
  local recon_id="$2"
  local token="$3"
  local comment="$4"

  local tmp status
  tmp=$(mktemp)
  status=$(curl -sS -o "$tmp" -w '%{http_code}' -X POST \
    "$base_url/api/reconciliations/$recon_id/run" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d "{\"triggerType\":\"MANUAL_API\",\"comments\":\"$comment\",\"initiatedBy\":\"local-dev\"}")

  if [[ "$status" -ne 200 ]]; then
    printf 'Run trigger failed for reconciliation %s (status %s).\n' "$recon_id" "$status" >&2
    cat "$tmp" >&2 || true
    rm -f "$tmp"
    return 1
  fi

  cat "$tmp"
  rm -f "$tmp"
}

build_ingestion_cli() {
  local cli_dir="$ROOT_DIR/examples/integration-harness"
  local cli_jar="$cli_dir/target/integration-ingestion-cli.jar"
  if [[ -f "$cli_jar" ]]; then
    printf '%s' "$cli_jar"
    return 0
  fi

  printf 'Building integration ingestion CLI...\n'
  (cd "$ROOT_DIR" && "$ROOT_DIR/backend/mvnw" -q -pl examples/integration-harness -am package -DskipTests)

  if [[ ! -f "$cli_jar" ]]; then
    printf 'Failed to build integration ingestion CLI at %s.\n' "$cli_jar" >&2
    return 1
  fi

  printf '%s' "$cli_jar"
}

seed_examples() {
  require_http_tools
  require_command java "Install Java 17+ to run the ingestion CLI."

  local base_url="${BASE_URL:-http://localhost:8080}"
  local health_url="$base_url/actuator/health"

  if ! curl -sS "$health_url" | jq -e '.status == "UP"' >/dev/null; then
    printf 'Backend health check failed at %s. Ensure the platform is running (e.g. ./scripts/local-dev.sh all ...).\n' "$health_url" >&2
    return 1
  fi

  local admin_user="${ADMIN_USERNAME:-admin1}"
  local admin_password="${ADMIN_PASSWORD:-password}"
  local ops_user="${OPS_USERNAME:-ops1}"
  local ops_password="${OPS_PASSWORD:-password}"

  printf 'Authenticating admin user (%s)...\n' "$admin_user"
  local admin_token
  if ! admin_token=$(login_user "$base_url" "$admin_user" "$admin_password"); then
    return 1
  fi

  local payload_dir="$ROOT_DIR/examples/integration-harness/payloads"
  if [[ ! -d "$payload_dir" ]]; then
    printf 'Payload directory not found at %s.\n' "$payload_dir" >&2
    return 1
  fi

  local -a recon_codes=()
  local -a recon_ids=()
  local payload
  for payload in "$payload_dir"/*.json; do
    [[ -e "$payload" ]] || continue
    local code
    code=$(jq -r '.code // empty' "$payload")
    printf 'Configuring reconciliation %s...\n' "$code"
    local recon_id
    if ! recon_id=$(upsert_reconciliation_payload "$base_url" "$payload" "$admin_token"); then
      return 1
    fi
    recon_codes+=("$code")
    recon_ids+=("$recon_id")
  done

  local cli_jar
  if ! cli_jar=$(build_ingestion_cli); then
    return 1
  fi

  printf 'Running ingestion CLI to load sample batches...\n'
  java -jar "$cli_jar" \
    --base-url "$base_url" \
    --username "$admin_user" \
    --password "$admin_password" \
    --scenario all

  printf 'Authenticating operations user (%s)...\n' "$ops_user"
  local ops_token
  if ! ops_token=$(login_user "$base_url" "$ops_user" "$ops_password"); then
    return 1
  fi

  local idx
  for idx in "${!recon_ids[@]}"; do
    local recon_id="${recon_ids[$idx]}"
    local code="${recon_codes[$idx]}"
    printf 'Triggering reconciliation run for %s...\n' "$code"
    local run_payload
    if ! run_payload=$(trigger_manual_run "$base_url" "$recon_id" "$ops_token" "Local sample bootstrap"); then
      return 1
    fi
    local summary
    summary=$(echo "$run_payload" | jq -c '.summary // empty')
    printf 'Run summary for %s: %s\n' "$code" "${summary:-<unavailable>}"
  done

  printf 'Sample scenarios installed successfully.\n'
}

main() {
  if [[ $# -lt 1 ]]; then
    usage
    exit 1
  fi

  local command="$1"
  shift

  case "$command" in
    bootstrap)
      bootstrap_deps
      ;;
    infra)
      if [[ $# -lt 1 ]]; then
        printf 'Missing infra subcommand.\n' >&2
        usage
        exit 1
      fi
      local subcommand="$1"
      shift
      case "$subcommand" in
        start)
          infra_start
          ;;
        stop)
          infra_stop
          ;;
        status)
          infra_status
          ;;
        logs)
          infra_logs "$@"
          ;;
        clean)
          infra_clean
          ;;
        *)
          printf 'Unknown infra subcommand: %s\n' "$subcommand" >&2
          usage
          exit 1
          ;;
      esac
      ;;
    backend)
      run_backend "$@"
      ;;
  frontend)
      run_frontend "$@"
      ;;
    all)
      run_all "$@"
      ;;
    seed)
      seed_examples
      ;;
    stop)
      stop_all
      ;;
    help|-h|--help)
      usage
      ;;
    *)
      printf 'Unknown command: %s\n' "$command" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
