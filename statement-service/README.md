# Statement Service (Dockerized)

## Quickstart (Docker Compose)
1. Build & start:
   ```bash
   cd docker
   docker-compose up --build
   ```
2. The API will be available at `http://localhost:8080`.

## Notes
- Database is Postgres (port 5432).
- Files are persisted to `./files` (mounted into the app as `/data/files`).
- Flyway runs migrations on startup.

