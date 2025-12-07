#!/usr/bin/env sh
set -euo pipefail

# docker-entrypoint.sh - render vault.hcl.tpl -> vault.hcl then exec vault server
# expects vault.hcl.tpl to be mounted at /vault/config/vault.hcl.tpl

TEMPLATE="/vault/config/vault.hcl.tpl"
TARGET="/vault/config/vault.hcl"
INIT_DIR="/vault/init"

# ensure init dir exists
mkdir -p "${INIT_DIR}"
chmod 700 "${INIT_DIR}"

# Render template if present
if [ -f "${TEMPLATE}" ]; then
  echo "[entrypoint] Rendering ${TEMPLATE} -> ${TARGET}"
  envsubst < "${TEMPLATE}" > "${TARGET}"
  chmod 644 "${TARGET}"
  echo "---- rendered /vault/config/vault.hcl ----"
  sed -n '1,160p' "${TARGET}" || true
  echo "---- end rendered ----"
else
  echo "[entrypoint] No template ${TEMPLATE} found - expecting /vault/config/vault.hcl directly"
fi

# ensure Vault binary present
if ! command -v vault >/dev/null 2>&1; then
  echo "[entrypoint] vault binary not found in image - aborting"
  exit 1
fi

# If command passed is 'server -config=...' then run it (default)
echo "[entrypoint] Starting vault server with: $*"
exec vault "$@"
