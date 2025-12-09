### Statement Service Platform

A multi-module platform for secure storage and delivery of monthly account statements.

The platform provides:

- A Spring Boot `statement-service` for:
    - Secure upload and encrypted storage of PDF statements
    - Time-limited, signed download links
    - Search APIs for statements
    - Audit logging for uploads, downloads, and link usage
- A Spring Cloud `config-server` that centralises configuration with Git and Vault backends
- An `infra` module with Docker Compose for local development:
    - PostgreSQL database
    - HashiCorp Vault for secrets management
    - Keycloak for authentication and authorisation
    - Config Server wired to a Git-style `config-repo`

This project is designed as a production-grade reference for secure file statement delivery.

---

### Repository Structure

- `statement-service/`
    - Spring Boot application exposing the Statement Upload & Search API
    - Implements encryption, secure download links, auditing, and role-based security
- `config-server/`
    - Spring Cloud Config Server
    - Reads configuration from `infra/config-repo` and Vault
- `infra/`
    - `docker-compose.yml` for database, Vault, Keycloak, and Config Server
    - Initialisation scripts for Postgres and Vault
    - Keycloak realm definition with roles and clients
    - Bruno collections for exercising the Statement API
- `infra/config-repo/`
    - Git-style configuration repo mounted into Config Server
- `README.md`
    - This file

---

### Core Features

- **Secure uploads**
    - PDF-only uploads via `/api/v1/statements/upload`
    - Request must include `X-Message-Digest` (SHA-256 hex) header that matches the uploaded file
    - Detailed validation with RFC 7807 `application/problem+json` error responses

- **Encrypted storage**
    - Statements encrypted with AES-GCM using a master key from `MasterKeyProvider`
    - Random per-file IV, stored at the start of each encrypted file
    - Account numbers hashed for search with `SHA-256`

- **Time-limited signed download links**
    - Signed links generated per statement via `/api/v1/statements/link/{statementId}`
    - Token and expiry stored in `SignedLink` entities
    - Single-use links supported
    - Download via `/api/v1/statements/download/{fileName}?expires=...&signature=...`

- **Audit logging**
    - Every successful upload and download is recorded in `AuditLog`
    - Failed downloads include failure reason (expired, used, invalid signature, missing file, decryption failure, etc.)
    - Audit writes are performed asynchronously using a virtual-thread executor
    - Audit logs retrievable via `/api/v1/statements/audit/logs`

- **Security model**
    - All API operations protected by JWT bearer tokens (Keycloak)
    - Fine-grained roles enforced at the HTTP layer (`Upload`, `Download`, `Search`, `GenerateSignedLink`, `AuditLogsSearch`)
    - Problem-detail based responses for unauthenticated/unauthorised requests

- **Observability & operations**
    - Correlation ID support via `x-correlation-id` header (propagated into logs)
    - Centralised structured logging with a `LoggingAspect` for controllers and services
    - Actuator endpoints (health, metrics, Prometheus) exposed under `/api/v1/statements/actuator`

---

### Technology Stack

- **Language / Runtime**: Java 21 (Eclipse Temurin images)
- **Frameworks**:
    - Spring Boot (web, validation, security)
    - Spring Security (resource server / JWT)
    - Spring Data JPA (PostgreSQL)
    - Spring Cloud Config
- **Security & Auth**:
    - Keycloak as OAuth2 / OpenID Connect provider
    - HashiCorp Vault for master key and secrets
- **Build & Packaging**: Maven, multi-stage Dockerfile
- **Documentation**: OpenAPI 3 (`statement-service-v1-openapi.yaml`), Springdoc

---

### Prerequisites

To build and run the full platform locally you will need:

- Java 21 (for direct `mvn spring-boot:run` usage)
- Maven 3.9+
- Docker and Docker Compose
- `curl` or HTTP client (Bruno, Postman, HTTPie) for exercising the API

Ensure Docker has enough memory to run Postgres, Vault, Keycloak, and Config Server simultaneously.

---

### Quick Start (Local Development)

#### 1. Build all Java modules

From the project root:

```bash
mvn clean install
```

#### 2. Start infrastructure (DB, Vault, Keycloak, Config Server)

---

#### Initial environment bootstrap (one‑time / nuclear option)

From the `infra` directory:

```bash
cd infra
cp .env.example .env   # if provided; otherwise create .env with the required variables
# Edit .env to set passwords, secrets, and ports as needed

./bootstrap_all.sh
```

**Important:**

- Only run `./bootstrap_all.sh` when you:
    - Set up a **new environment** for the first time, or
    - Want to **scrap everything and start fresh** (it is a *nuclear option* that tears down and recreates infra state).
- Do **not** run `bootstrap_all.sh` as part of normal day‑to‑day start/stop cycles.

After `bootstrap_all.sh` has completed successfully once for a given environment, use regular Docker Compose commands to control the stack.

#### 3. Start/stop infrastructure after bootstrap

Once the environment has been bootstrapped, you can bring the infra stack up and down without re‑running the bootstrap script:

```bash
cd infra

# Start services (Postgres, Vault, Keycloak, Config Server, etc.)
docker compose up -d

# Stop services
docker compose down
```

You can add `--build` on `docker compose up` only if you change the infra images or need to rebuild them.

Services exposed locally:

- Postgres: `localhost:5432`
- Vault: `http://localhost:8200`
- Keycloak: `http://localhost:8081`
- Config Server: `http://localhost:8888`

Wait for all services to become healthy (check Docker Compose logs or healthchecks).

#### 3. Run statement-service (locally via Maven)

In a separate terminal, from the project root:

```bash
mvn -pl statement-service spring-boot:run
```

By default the service starts on:

- Statement Service: `http://localhost:8080`

The application will fetch configuration from the Config Server if `spring.config.import=optional:configserver:...` is enabled for the active profile.

---

### Running statement-service in Docker

The `statement-service/docker/Dockerfile` is a multi-stage Dockerfile that builds and runs the service.

#### Build the image

From the project root:

```bash
docker build -f statement-service/docker/Dockerfile -t statement-service:latest .
```

#### Run the container

Example (adjust environment variables to match your environment / config server):

```bash
docker run --rm \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=develop \
  -e SPRING_CONFIG_IMPORT=optional:configserver:http://host.docker.internal:8888 \
  --name statement-service \
  statement-service:latest
```

If you run the app container on the same Docker network as the Compose stack in `infra/`, you can point `SPRING_CONFIG_IMPORT` and datasource URLs to `config-server`, `db`, and `vault` hostnames.

The container entrypoint uses `wait-for-config.sh` and `entrypoint.sh` to delay startup until the Config Server is reachable.

---

### Configuration

Most configuration is centralised via Spring Cloud Config and Vault, but the local `application.yml` contains key defaults:

- `server.port`: `8080`
- `spring.application.name`: `statement-service`
- `spring.cloud.config.enabled`: `true` (for `develop` profile)
- Logging pattern includes `correlationId=%X{correlationId}`
- Springdoc API docs and Swagger UI paths:
    - `/api/v1/statements/v3/api-docs`
    - `/api/v1/statements/swagger-ui/index.html`
- Actuator endpoints exposed under `/api/v1/statements/actuator`

Additional configuration (datasource, Vault, security, etc.) lives in `infra/config-repo` and Vault policies set up by the infra scripts.

---

### Security & Authentication

#### JWT & Roles

The service is configured as an OAuth2 resource server using JWTs issued by Keycloak.

`SecurityConfig` enforces:

- Stateless sessions (`SessionCreationPolicy.STATELESS`)
- CSRF disabled for API paths specified in `SecurityEndpointsProperties`
- Endpoint-specific role mappings:
    - `/api/v1/statements/upload` → `ROLE_Upload`
    - `/api/v1/statements/audit/logs` → `ROLE_AuditLogsSearch`
    - `/api/v1/statements/search` → `ROLE_Search`
    - `/api/v1/statements/*/link` → `ROLE_GenerateSignedLink`
    - `/api/v1/statements/download/**` → `ROLE_Download`

Access errors:

- `401 Unauthorized` – unauthenticated; returned as RFC 7807 `ProblemDetail` with type `/errors/authentication`
- `403 Forbidden` – authenticated but missing role; returned as `ProblemDetail` with type `/errors/authorization`

The JWT to Spring Security authority mapping is handled by `KeycloakRoleConverter`.

#### Signed Download Links

- Links are generated for a specific statement via `SignedLinkService`.
- The link contains:
    - Path: `/api/v1/statements/download/{fileName}`
    - Query parameters: `expires` (epoch seconds) and `signature` (signed token)
- The token is stored server-side in a `SignedLink` row with:
    - `statementId`
    - `expiresAt`
    - `singleUse` flag and `used` flag
- `DownloadService.validateAndStreamDetailed` performs:
    - Token lookup with `validateAndConsume` (marks single-use links as used)
    - Expiry check
    - Statement lookup
    - File existence check
    - Decryption & streaming
    - Detailed audit logging on success and failure

Note: By default, `/download/**` still requires a JWT with `ROLE_Download`. If your production model should allow customers to use signed links without authentication, you can relax this in `SecurityConfig` by permitting `/download/**` and relying solely on the signed token + DB constraints.

---

### Encryption & Data Protection

`EncryptionService` handles cryptographic operations:

- Encryption:
    - AES-GCM (`AES/GCM/NoPadding`)
    - 128-bit authentication tag
    - 12-byte random IV per file (written as the first bytes of the file)
    - Master key material is supplied by `MasterKeyProvider` (typically backed by Vault)
- Decryption:
    - Reads and validates the IV prefix
    - Streams decrypted content via `CipherInputStream` for efficient downloads
- SHA-256 digests:
    - `computeSha256Hex(MultipartFile file)` computes the upload digest used to validate `X-Message-Digest`
- Account number hashing:
    - `computeAccountNumberHash(String accountNumber)` hashes clear-text account numbers before persistence or search

Ensure the master key and Vault configuration are properly secured in production and that key rotation procedures are documented for your environment.

---

### Auditing & Logging

#### Audit Logs

`AuditService` provides asynchronous audit logging:

- Actions include (examples):
    - `UPLOAD_SUCCESS`
    - `DOWNLOAD_SUCCESS`
    - `DOWNLOAD_FAILED`
- Each `AuditLog` entry contains:
    - `id` (UUID)
    - `action`
    - `statementId`
    - `accountNumber` (or hash, depending on storage model)
    - `signedLinkId`
    - `performedBy` (from the security context, falling back to `system`/`admin`)
    - `performedAt`
    - `details` map (IP, user-agent, download failure reason, masked token, errors, etc.)

Audit logs can be queried via `/api/v1/statements/audit/logs` with filters (account number, date range, pagination).

#### Application Logging

`LoggingAspect` wraps all controllers and services to provide structured logs:

- At INFO level:
    - Logs entry and exit of methods with timing but without sensitive arguments
- At DEBUG level:
    - Logs formatted arguments (with care to avoid dumping large bodies or sensitive data)
    - Summarises return values (e.g. `ResponseEntity(status=200, body=UploadResponse)`)
- Special handling for:
    - `MultipartFile` (logs name, content-type, size only)
    - Long `String` values (truncated)
    - Byte arrays (`byte[n]`)

Correlation IDs:

- `x-correlation-id` header is accepted on incoming requests (see OpenAPI spec)
- The value is placed into MDC as `correlationId`
- Logging pattern includes `[correlationId=%X{correlationId}]` for tracing

---

### API Documentation

The API is documented using OpenAPI 3.0:

- OpenAPI file: `statement-service/src/main/resources/openapi/statement-service-v1-openapi.yaml`
- Generated Spring interfaces: `statement-service/target/generated-sources/openapi/...`
- Runtime docs via Springdoc:
    - JSON: `GET /api/v1/statements/v3/api-docs`
    - UI: `GET /api/v1/statements/swagger-ui/index.html`

Key endpoints (all under `/api/v1/statements`):

- `POST /upload` – upload PDF statement with `X-Message-Digest` header
- `GET /link/{statementId}` – retrieve statement metadata and a signed download link
- `GET /search` – search statements by account number and/or date (paged)
- `GET /download/{fileName}` – download statement using signed link
- `GET /audit/logs` – retrieve paged audit logs

Refer to the OpenAPI spec for full request/response schemas and error formats.

---

### Testing

Unit and integration tests live under `statement-service/src/test/java`.

To run tests for all modules:

```bash
mvn test
```

To run tests for `statement-service` only:

```bash
mvn -pl statement-service test
```

Some tests target:

- Controller behaviour (e.g. `AdminControllerTest`)
- Mapping layers (e.g. `UploadResponseApiMapperTest`)
- Service logic (`DownloadService`, `StatementUploadService`, etc.)

You are encouraged to add additional integration tests (e.g. using Testcontainers for Postgres) to validate the full upload → link → download flow.

---

### Using the API (Example Flows)

#### Upload a statement

1. Obtain an access token from Keycloak with the `Upload` role.
2. Compute the SHA-256 digest of your PDF file (hex string).
3. Call:

```bash
curl -X POST "http://localhost:8080/api/v1/statements/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Message-Digest: $DIGEST" \
  -F "file=@statement.pdf;type=application/pdf" \
  -F "accountNumber=123456789" \
  -F "date=2025-11-01"
```

4. The response includes `statementId`, `uploadedAt`, and file metadata.

#### Get a signed download link

```bash
curl -X GET "http://localhost:8080/api/v1/statements/link/$STATEMENT_ID" \
  -H "Authorization: Bearer $TOKEN"
```

The response body contains a `downloadLink` URL.

#### Download the PDF

```bash
curl -L -X GET "$DOWNLOAD_LINK" -H "Authorization: Bearer $TOKEN" -o statement.pdf
```

(If you make `/download/**` public in `SecurityConfig`, the token header becomes optional for that endpoint.)

#### Query audit logs

```bash
curl -X GET "http://localhost:8080/api/v1/statements/audit/logs?accountNumber=123456789&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN_WITH_AUDIT_ROLE"
```

---
