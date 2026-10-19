#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/deploy/docker/.env"
ENV_EXAMPLE="${ROOT_DIR}/deploy/docker/.env.example"
COMPOSE_FILE="${ROOT_DIR}/deploy/docker/docker-compose.yml"

fail() {
  printf 'Error: %s\n' "$1" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || fail "Docker is not installed or not available in PATH."
docker compose version >/dev/null 2>&1 || fail "Docker Compose plugin is not available."

[ -f "${COMPOSE_FILE}" ] || fail "Missing deploy/docker/docker-compose.yml."

if [ ! -f "${ENV_FILE}" ]; then
  [ -f "${ENV_EXAMPLE}" ] || fail "Missing deploy/docker/.env.example."
  cp "${ENV_EXAMPLE}" "${ENV_FILE}"
  printf 'Created deploy/docker/.env from deploy/docker/.env.example.\n'
  printf 'Edit deploy/docker/.env and replace placeholder secrets, then rerun this script.\n'
  exit 1
fi

cd "${ROOT_DIR}"
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml up -d --build
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml ps
