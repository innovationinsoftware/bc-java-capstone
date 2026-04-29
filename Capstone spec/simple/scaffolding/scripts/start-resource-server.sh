#!/usr/bin/env bash
#
# Start the Resource Server (banking API) on port 8081.
#
# Requires: JDK 17, Maven 3.9+, Oracle reachable at $ORACLE_URL,
#           mock-auth running on port 9000 (so the resource-server can
#           fetch the JWKS for token validation).
#
# Usage:   ./scripts/start-resource-server.sh
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
exec mvn -pl resource-server -am spring-boot:run
