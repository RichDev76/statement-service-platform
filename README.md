# Statement Platform

Multi-module project containing:

- `statement-service`: Spring Boot service for storing and serving statements
- `config-server`: Spring Cloud Config Server with Git + Vault backends
- `infra`: Docker Compose, Keycloak realm, scripts, Bruno collections
- `statement-config-repo`: Git-style config repo for Config Server

## Quick start

```bash
# Build all Java modules
mvn clean install

# Start full stack (from infra/)
cd infra
docker compose up --build
```

Then:

- Statement Service: http://localhost:8080
- Config Server: http://localhost:8888
- Keycloak: http://localhost:8081
- Vault (dev): http://localhost:8200
```
