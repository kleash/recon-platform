#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
COMMON_DIR="$PROJECT_ROOT/examples/common"
EXAMPLE_DIR="$PROJECT_ROOT/examples/securities-position"

"$BACKEND_DIR/mvnw" -f "$BACKEND_DIR/pom.xml" clean install -DskipTests -Dspring-boot.repackage.skip=true
"$BACKEND_DIR/mvnw" -f "$COMMON_DIR/pom.xml" clean install -DskipTests
"$BACKEND_DIR/mvnw" -f "$EXAMPLE_DIR/pom.xml" clean test
