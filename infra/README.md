# Infra Setup

This repository contains a local development infra for the Statement Service project.

## Components
- **Postgres**: used by the Statement Service (`statementdb`). DB init script creates the app DB only.
- **Vault**: HashiCorp Vault using file storage (persisted in Docker volume `vault-data`).
- **Keycloak**: runs in Quarkus `start-dev` mode and persists data to `keycloak-data` volume. Realm import happens on first start.
- **Config Server**: Spring Cloud Config server that can read from Vault.

## Quick start
1. Copy `.env.example` to `.env` and set credentials.
2. Run `./new-env.sh` to create a complete clean environment.
3. Visit Keycloak: `http://localhost:8081` (admin credentials from `.env`).
4. Vault UI: `http://localhost:8200` (use root token from `vault_init.json`).
5. Config Server: `http://localhost:8888/actuator/health`.

## Reinitialize
To wipe and recreate the environment:

```bash
./new-env.sh