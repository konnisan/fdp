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
    echo "[WARN] Docker CLI exists but daemon is not reachable by current user"
    missing=1
  fi
fi

for dir in "${FDP_WORKSPACE_ROOT:-/data/fdp/workspaces}" "${FDP_STATIC_ROOT:-/data/fdp/sites}" "${FDP_DATA_ROOT:-/data/fdp/data}"; do
  if [ -d "$dir" ]; then
    echo "[OK] Directory exists: $dir"
  else
    echo "[INFO] Directory will need to be created: $dir"
  fi
done

if [ "$missing" -ne 0 ]; then
  echo
  echo "Runtime check failed. Install/fix missing dependencies before setting FDP_EXECUTION_ENABLED=true."
  exit 1
fi

echo
echo "Runtime check passed."
