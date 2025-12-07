#!/usr/bin/env bash
set -euo pipefail

echo "==> wiping existing containers & volumes"
docker compose down || true
# remove persistent volumes used by this stack
docker volume rm vault-data keycloak-data db-data 2>/dev/null || true
rm -f vault_init.json || true

echo "==> building images"
docker compose build --no-cache

echo "==> bringing up stack"
docker compose up -d

# wait for keycloak to start (approx)
echo "Waiting for Keycloak to be ready..."
until docker compose exec -T keycloak /opt/keycloak/bin/kc.sh show-config >/dev/null 2>&1; do
  sleep 1
done

# ensure vault is responding to status API before init
echo "Waiting for vault to respond..."
until docker compose exec -T vault sh -c 'vault status -format=json' >/dev/null 2>&1; do
  sleep 1
done

# initialize vault only if not initialized
if [ ! -f vault_init.json ]; then
  echo "Initializing Vault..."
  docker compose exec -T vault sh -c 'vault operator init -key-shares=1 -key-threshold=1 -format=json' > vault_init.json
  chmod 600 vault_init.json
  UNSEAL_KEY=$(jq -r '.unseal_keys_b64[0]' vault_init.json)
  docker compose exec -T vault sh -c "vault operator unseal ${UNSEAL_KEY}"
  echo "Vault initialized and unsealed. Saved vault_init.json"
else
  echo "vault_init.json already exists; skipping init. If you want fresh run, remove vault_init.json and volumes."
fi

# bootstrap secrets if root token is present
if command -v jq >/dev/null 2>&1; then
  ROOT_TOKEN=$(jq -r '.root_token' vault_init.json 2>/dev/null || true)
  if [ -n "${ROOT_TOKEN:-}" ] && [ "${ROOT_TOKEN}" != "null" ]; then
    echo "Bootstrapping initial secrets..."
    docker compose exec -T vault sh -c "VAULT_TOKEN=${ROOT_TOKEN} vault secrets enable -version=2 -path=secret kv" || true
    docker compose exec -T vault sh -c "VAULT_TOKEN=${ROOT_TOKEN} vault kv put secret/keycloak/db username=kcuser password='Kc9Lx4pZ7wQ2sV6mD8hJ3' url='jdbc:postgresql://db:5432/keycloak'" || true
    docker compose exec -T vault sh -c "VAULT_TOKEN=${ROOT_TOKEN} vault kv put secret/statement-service db.username=statement_user db.password='qP7vL1zM9teR4sC6yB8nW' db.name=statementdb" || true
    docker compose exec -T vault sh -c "cat > /tmp/statement-service-policy.hcl <<'EOF'
path \"secret/data/statement-service*\" {
  capabilities = [\"read\",\"list\"]
}
path \"secret/data/keycloak/*\" {
  capabilities = [\"read\",\"list\"]
}
EOF
VAULT_TOKEN=${ROOT_TOKEN} vault policy write statement-service-policy /tmp/statement-service-policy.hcl" || true
    docker compose exec -T vault sh -c "VAULT_TOKEN=${ROOT_TOKEN} vault auth enable approle" || true
    docker compose exec -T vault sh -c "VAULT_TOKEN=${ROOT_TOKEN} vault write -f auth/approle/role/statement-service-role token_policies='statement-service-policy' token_ttl=1h token_max_ttl=4h" || true
    ROLE_ID=$(docker compose exec -T vault sh -c "VAULT_TOKEN=${ROOT_TOKEN} vault read -field=role_id auth/approle/role/statement-service-role/role-id" | tr -d '\r\n')
    SECRET_ID_JSON=$(docker compose exec -T vault sh -c "VAULT_TOKEN=${ROOT_TOKEN} vault write -f -format=json auth/approle/role/statement-service-role/secret-id")
    SECRET_ID=$(echo "${SECRET_ID_JSON}" | jq -r '.data.secret_id') || true
    echo "ROLE_ID=${ROLE_ID}"
    echo "SECRET_ID=${SECRET_ID}"
  fi
fi

# final health checks
sleep 2
echo "Final container status:"
docker compose ps

echo "Check Keycloak logs for realm import:"
docker compose logs keycloak --tail=200 | grep -i import || true

echo "Done."
