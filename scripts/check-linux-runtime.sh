#!/usr/bin/env bash
set -euo pipefail

required=(git docker nginx rsync curl java)
missing=0

echo "FDP Linux runtime check"
echo "======================="

for command in "${required[@]}"; do
  if command -v "$command" >/dev/null 2>&1; then
    printf "[OK]   %-8s %s\n" "$command" "$(command -v "$command")"
  else
    printf "[MISS] %-8s not found\n" "$command"
    missing=1
  fi
done

echo
if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    echo "[OK] Docker daemon is reachable"
  else
    echo "[MISS] Docker CLI exists but daemon is not reachable by current user"
    missing=1
  fi
fi

for dir in \
  "${FDP_WORKSPACE_ROOT:-/data/fdp/workspaces}" \
  "${FDP_STATIC_ROOT:-/data/fdp/sites}" \
  "${FDP_DATA_ROOT:-/data/fdp/data}" \
  "${FDP_PROJECT_ROOT:-/data/fdp/projects}"; do
  if [ -d "$dir" ]; then
    if [ -w "$dir" ]; then
      echo "[OK] Writable directory: $dir"
    else
      echo "[MISS] Directory is not writable by current user: $dir"
      missing=1
    fi
  else
    echo "[INFO] Directory will need to be created: $dir"
  fi
done

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  network="${FDP_DOCKER_NETWORK:-fdp-network}"
  if docker network inspect "$network" >/dev/null 2>&1; then
    echo "[OK] Docker network exists: $network"
  else
    echo "[INFO] Docker network will be created on first managed-project start: $network"
  fi

  preview_container="${FDP_PREVIEW_NGINX_CONTAINER:-fdp-preview-nginx}"
  if docker inspect "$preview_container" >/dev/null 2>&1; then
    echo "[OK] Preview Nginx container exists: $preview_container"
  else
    echo "[WARN] Preview Nginx container not found: $preview_container"
    echo "       Managed projects can still be materialized/started, but public preview switching is not ready."
  fi
fi

echo
if [ "$missing" -ne 0 ]; then
  echo "Runtime check failed. Fix missing dependencies/permissions before setting FDP_EXECUTION_ENABLED=true."
  exit 1
fi

echo "Runtime check passed."
echo "Note: NGINX service mode additionally requires the selected project runtime image to contain nginx and supervisord."
