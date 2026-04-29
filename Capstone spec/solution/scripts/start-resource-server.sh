#!/usr/bin/env bash
# Start the Resource Server (JWT-secured REST API) on port 8082.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../backend/resource-server"

echo "Starting Resource Server on http://localhost:8082 ..."
mvn spring-boot:run
