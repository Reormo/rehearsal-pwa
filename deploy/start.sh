#!/usr/bin/env bash
set -Eeuo pipefail

: "${PORT:?Railway must provide PORT}"

export BACKEND_PORT="${BACKEND_PORT:-8080}"

java -jar /app/backend.jar &
backend_pid=$!

caddy run --config /etc/caddy/Caddyfile --adapter caddyfile &
caddy_pid=$!

shutdown() {
  kill -TERM "$backend_pid" "$caddy_pid" 2>/dev/null || true
}

trap shutdown TERM INT

set +e
wait -n "$backend_pid" "$caddy_pid"
status=$?
set -e

shutdown
wait "$backend_pid" 2>/dev/null || true
wait "$caddy_pid" 2>/dev/null || true

exit "$status"
