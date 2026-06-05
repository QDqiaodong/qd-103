#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
REPO_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker command not found"
  exit 1
fi

docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d --build --remove-orphans

echo "frontend: http://localhost"
echo "backend:  http://localhost:8080/api/questionnaires"
echo "repo:     $REPO_DIR"
