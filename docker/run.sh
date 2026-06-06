#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
REPO_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$REPO_DIR/.env"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker command not found"
  exit 1
fi

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

COMPOSE_CMD="docker compose --env-file \"$ENV_FILE\" -f \"$REPO_DIR/docker-compose.yml\""

# Clear stale containers from the same compose project before recreating services.
sh -c "$COMPOSE_CMD down --remove-orphans >/dev/null 2>&1 || true"
sh -c "$COMPOSE_CMD up -d --build --remove-orphans"

FRONTEND_PORT_VALUE="${FRONTEND_PORT:-18183}"
BACKEND_PORT_VALUE="${BACKEND_PORT:-18184}"
MYSQL_PORT_VALUE="${MYSQL_PORT:-13316}"
REDIS_PORT_VALUE="${REDIS_PORT:-16379}"

printf '\n构建完成，可访问以下地址：\n'
printf 'frontend: http://localhost:%s\n' "$FRONTEND_PORT_VALUE"
printf 'backend:  http://localhost:%s/api/questionnaires\n' "$BACKEND_PORT_VALUE"
printf 'mysql:    localhost:%s\n' "$MYSQL_PORT_VALUE"
printf 'redis:    localhost:%s\n' "$REDIS_PORT_VALUE"
printf 'repo:     %s\n' "$REPO_DIR"
