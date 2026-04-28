#!/usr/bin/env bash
#
# Start the mock Authorization Server on port 9000.
#
# This must be the FIRST service started — both the BFF and the Resource
# Server fetch /.well-known/openid-configuration from it on boot.
#
# Pre-registered users:
#   alice / alice  (CUSTOMER)
#   admin / admin  (ADMIN — set via UPDATE BANK_USERS in Oracle)
#
# Usage:   ./scripts/start-mock-auth.sh
# Stop:    Ctrl-C

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$SCAFFOLD_DIR/backend"
exec mvn -pl mock-auth -am spring-boot:run
