#!/usr/bin/env bash
set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run this script" >&2
  exit 1
fi

BASE_URL=${BASE_URL:-http://localhost:8080/api}
USERNAME=${USERNAME:-admin1}
PASSWORD=${PASSWORD:-password}

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
EXAMPLE_DIR=$(dirname "$SCRIPT_DIR")
PAYLOAD_DIR="${EXAMPLE_DIR}/payloads"
DATA_DIR="${EXAMPLE_DIR}/data"
ARTIFACT_DIR="${EXAMPLE_DIR}/artifacts"

mkdir -p "$ARTIFACT_DIR"

echo "Logging in as ${USERNAME} against ${BASE_URL}..."
LOGIN_RESPONSE=$(curl -s \
  -H 'Content-Type: application/json' \
  -X POST \
  "${BASE_URL}/auth/login" \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}")
TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "Failed to authenticate. Response: $LOGIN_RESPONSE" >&2
  exit 1
fi

echo "$LOGIN_RESPONSE" | jq '.' > "${ARTIFACT_DIR}/login-response.json"

echo "Creating reconciliation definition from payload ${PAYLOAD_DIR}/reconciliation.json..."
CREATE_RESPONSE=$(curl -s \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -X POST \
  "${BASE_URL}/admin/reconciliations" \
  --data @"${PAYLOAD_DIR}/reconciliation.json")

echo "$CREATE_RESPONSE" | jq '.' > "${ARTIFACT_DIR}/reconciliation-response.json"

DEFINITION_ID=$(echo "$CREATE_RESPONSE" | jq -r '.id')
ANCHOR_SOURCE=$(echo "$CREATE_RESPONSE" | jq -r '.sources[] | select(.anchor == true) | .code' | head -n 1)
if [[ -z "$DEFINITION_ID" || "$DEFINITION_ID" == "null" ]]; then
  echo "Failed to create reconciliation definition. Inspect ${ARTIFACT_DIR}/reconciliation-response.json" >&2
  exit 1
fi
if [[ -z "$ANCHOR_SOURCE" || "$ANCHOR_SOURCE" == "null" ]]; then
  echo "Unable to determine anchor source from response. Inspect ${ARTIFACT_DIR}/reconciliation-response.json" >&2
  exit 1
fi

echo "Exporting schema for definition ${DEFINITION_ID}..."
SCHEMA_RESPONSE=$(curl -s \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/admin/reconciliations/${DEFINITION_ID}/schema")

echo "$SCHEMA_RESPONSE" | jq '.' > "${ARTIFACT_DIR}/schema-${DEFINITION_ID}.json"

METADATA_FILE="${PAYLOAD_DIR}/ingestion-metadata.json"
DATA_FILE="${DATA_DIR}/custody_feed.csv"

echo "Submitting ingestion batch for source ${ANCHOR_SOURCE}..."
INGESTION_RESPONSE=$(curl -s \
  -H "Authorization: Bearer ${TOKEN}" \
  -X POST \
  "${BASE_URL}/admin/reconciliations/${DEFINITION_ID}/sources/${ANCHOR_SOURCE}/batches" \
  -F "file=@${DATA_FILE};type=text/csv" \
  -F "metadata=@${METADATA_FILE};type=application/json")

echo "$INGESTION_RESPONSE" | jq '.' > "${ARTIFACT_DIR}/ingestion-response.json"

BATCH_STATUS=$(echo "$INGESTION_RESPONSE" | jq -r '.status // empty')
if [[ -z "$BATCH_STATUS" ]]; then
  echo "Ingestion may have failed. Inspect ${ARTIFACT_DIR}/ingestion-response.json" >&2
  exit 1
fi

echo "Bootstrap complete. Artifacts written to ${ARTIFACT_DIR}."
