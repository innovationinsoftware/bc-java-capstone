#!/usr/bin/env bash
#
# Start the BFF on port 8080.
#
# Requires: JDK 17, Maven 3.9+, mock-auth running on port 9000,
#           resource-server running on port 8081.
#
# Loads OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET, AUTH_SERVER_URL,
# RESOURCE_SERVER_URL from .env if present (otherwise defaults
# match the in-scaffold mock-auth + resource-server).
#
# Usage:   ./scripts/start-bff.sh
# Stop:    Ctrl-C

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCAFFOLD_DIR/.env"

if [ -f "$ENV_FILE" ]; then
  echo "Loading env vars from $ENV_FILE"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

cd "$SCAFFOLD_DIR/backend"
exec mvn -pl bff -am spring-boot:run
