# Manual testing

- **API**: `docker compose up --build -d` from the repo root serves http://localhost:8080 with the `local` profile (migrations plus seed data). Every API path is versioned under `/v1` (e.g. `/v1/time-deposits`). Exercise it with `curl`; the README's "Trying the endpoints" section lists the seeded Time Deposits and the Balances expected after one Balance Update. `docker compose down -v` resets to the seed data.
- **Swagger UI**: http://localhost:8080/swagger-ui.html, driven with the session's browser tools.
- **Auth**: not gated.
- **Wiring**: PostgreSQL only, defined in `compose.yaml`; app datasource defaults in `java/src/main/resources/application-local.yaml`. No external providers. The database has no host port; reach it with `docker compose exec postgres psql -U time_deposits`.
- **Logs**: `docker compose logs app`. Every line of a request carries its trace id, the one in the `X-Trace-Id` response header, as `[traceId-spanId]`.

## Gotchas

- Docker CLI pulls can hang on the Docker Desktop credential helper. Point `DOCKER_CONFIG` at a scratch directory whose `config.json` has no `credsStore`; leave `~/.docker/config.json` alone.
- When Docker is unavailable: run a PostgreSQL 18 server on a free port with a `time_deposits` database, user and password, then start the app from `java/` with `SPRING_PROFILES_ACTIVE=local SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:<port>/time_deposits ./mvnw spring-boot:run` (JDK 25). This skips the image build and compose wiring, so label that evidence degraded. With Homebrew `postgresql@18`, `initdb` and `pg_ctl` need `LC_ALL` set to a valid locale (e.g. `en_US.UTF-8`), and a long data-directory path needs `-c unix_socket_directories=''` (TCP only).
