#!/usr/bin/env bash
# Start the BFF (session-based OAuth2 client + reverse proxy) on port 8081.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../backend/bff"

echo "Starting BFF on http://localhost:8081 ..."
mvn spring-boot:run
