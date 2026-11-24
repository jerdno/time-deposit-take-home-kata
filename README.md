# Time Deposit Refactoring Kata

## Solution

A REST API over XA Bank's Time Deposits: list them, and run a Balance Update that adds one Monthly Interest to every Time Deposit. Spring Boot 4 on Java 25, PostgreSQL, a contract-first OpenAPI definition and a hexagonal layout. The original assignment is [below](#assignment); the domain terms are defined in [`GLOSSARY.md`](GLOSSARY.md).

### Prerequisites

Docker with Docker Compose v2 (Docker Desktop includes both). Nothing else: the application image is built inside Docker, so no JDK or Maven is needed. Port 8080 must be free.

### How to run

From the repository root:

```sh
docker compose up --build
```

This builds the application image, starts PostgreSQL, waits until it accepts connections, then starts the application with the `local` profile. On startup Flyway creates the schema and loads ten seeded Time Deposits. The first build downloads Maven and the dependencies, which takes a few minutes. The API is ready when the log shows `Started TimeDepositApplication`.

- To stop, press `Ctrl+C` or run `docker compose down`. The data stays in the `postgres-data` volume, so Balance Updates survive a restart.
- To start again from the seed data, run `docker compose down -v`, then `docker compose up --build`.

### Trying the endpoints in Swagger UI

Open <http://localhost:8080/swagger-ui.html>. It shows the contract as written (`java/src/main/resources/openapi.yaml`, also served at <http://localhost:8080/openapi.yaml>) with its two operations. For each step, expand the operation, click **Try it out**, then **Execute**.

1. **`GET /v1/time-deposits`** returns `200` and the first page of Time Deposits (default `size` 20), ordered by `id`: the ten seeded Time Deposits with their Withdrawals, and `totalItems: 10`.
2. **`POST /v1/time-deposits/balance-updates`** returns `204` with no body. One Monthly Interest was added to the Balance of every Time Deposit. The interest is computed first, with no transaction open, and all the new Balances are then saved in one transaction. Leave the optional `Idempotency-Key` field empty for this walkthrough.
3. **`GET /v1/time-deposits`** again. A Time Deposit inside its Plan Type's Eligibility Window grew by one Monthly Interest: the annual rate divided by twelve, applied to the Balance, rounded to the cent. Every other Balance is unchanged, and Days never changes.

| id | Plan Type | Days | Balance before | Balance after | Why |
|---:|---|---:|---:|---:|---|
| 1 | basic | 30 | 10000.00 | 10000.00 | No interest in the first 30 days |
| 2 | basic | 31 | 10000.00 | 10008.33 | 1% / 12 of 10000.00 is 8.33 |
| 3 | basic | 400 | 25000.00 | 25020.83 | 1% / 12 of 25000.00 is 20.83; Basic has no upper limit |
| 4 | student | 30 | 5000.00 | 5000.00 | No interest in the first 30 days |
| 5 | student | 31 | 5000.00 | 5012.50 | 3% / 12 of 5000.00 is 12.50 |
| 6 | student | 365 | 5000.00 | 5012.50 | Day 365 is the last day of the Student window |
| 7 | student | 366 | 5000.00 | 5000.00 | No Student interest after the first year |
| 8 | premium | 31 | 50000.00 | 50000.00 | Premium interest starts after 45 days |
| 9 | premium | 45 | 50000.00 | 50000.00 | Premium interest starts after 45 days |
| 10 | premium | 46 | 50000.00 | 50208.33 | 5% / 12 of 50000.00 is 208.33 |

Withdrawals are history only and play no part in interest. Every POST without an `Idempotency-Key` adds another month, so a second POST grows the same Balances again. Of two POSTs that overlap in time, one returns `204` and the other returns `409 Conflict` and applies nothing (see [ADR 0001](docs/adr/0001-optimistic-locking-for-the-balance-update.md)).

To make a retry safe, send an `Idempotency-Key` header with the POST. The first call with a key runs the Balance Update; a later call with the same key returns `204` and adds no interest (see [ADR 0002](docs/adr/0002-idempotency-key-for-the-balance-update.md)). To try it in Swagger UI, type a value such as `april-2026` into the `Idempotency-Key` field and click **Execute** twice: both return `204`, and `GET /time-deposits` shows that only the first one added interest. Keys never expire and are kept in the `postgres-data` volume, so pick a value you have not used yet, or start from the seed data with `docker compose down -v`.

The same walkthrough with curl:

```sh
curl -s http://localhost:8080/v1/time-deposits
curl -s -i -X POST http://localhost:8080/v1/time-deposits/balance-updates
curl -s http://localhost:8080/v1/time-deposits
```

A keyed POST, run twice, adds interest once:

```sh
curl -s -i -X POST -H 'Idempotency-Key: march-2026' http://localhost:8080/v1/time-deposits/balance-updates
curl -s -i -X POST -H 'Idempotency-Key: march-2026' http://localhost:8080/v1/time-deposits/balance-updates
```

### Running the tests

The tests need JDK 25 and a running Docker daemon, because the integration tests start PostgreSQL with Testcontainers.

```sh
cd java
./mvnw verify
```

`verify` runs the unit, integration and ArchUnit tests and checks the formatting with Spotless (`./mvnw spotless:apply` fixes it). CI (`.github/workflows/ci.yml`) runs the same command on every push and pull request.

### Package layout

The code follows a hexagonal layout, and ArchUnit tests (`HexagonalArchitectureTest`) fail `./mvnw verify` when it is broken. The domain is `org.ikigaidigital` itself (`TimeDeposit`, `TimeDepositCalculator`, not its sub-packages) plus `org.ikigaidigital.plantype` (the Plan Types and `PlanTypeResolver`); it depends only on the JDK, the SLF4J API and itself. `org.ikigaidigital.application` holds the use cases, the in- and out-ports and the model they exchange (`application.model`), and may use Spring and the domain but no adapter. `org.ikigaidigital.adapter.web` (generated API interfaces, controller, the Problem Details handler in `web.exception`, web infrastructure) and `org.ikigaidigital.adapter.persistence` (out-port implementations, with the JPA entities in `persistence.entity` and the Spring Data repositories in `persistence.repository`) depend on the application layer and never on each other.

### AI-assisted development

The write-up of how AI was used on this solution (tools, setup, rules and prompts, and which parts were AI-assisted) is tracked in ticket 10. It is not in the repository yet and will be linked here when it lands.

### Assumptions

- "The application boots and stays up" needs an embedded web server, so the Spring MVC starter (Tomcat) is added before any endpoint exists. (ticket 2)
- A negative Balance is pinned as the calculator handles it today: interest is negative and the Balance shrinks, rounding half-up (away from zero) on a half cent. (ticket 3)
- Plan Type matching is case-sensitive today, so `"Basic"` earns no interest, the same as an unknown Plan Type such as `"gold"`. Both are pinned as "no interest, Balance unchanged" rather than rejected. (ticket 3)
- `TimeDepositCalculator`'s no-arg constructor was removed; it now takes a `PlanTypeResolver`. The README protects only `TimeDeposit` and the `updateBalance` signature, and the only caller in this repo was the test. (ticket 4)
- A missing (null) Plan Type is bad data like any unknown value: it is logged at `ERROR` and earns no interest. The original calculator threw a `NullPointerException` for it once Days passed 30. (ticket 4)
- The README asks for "a list" of Time Deposits; `GET /v1/time-deposits` returns an envelope `{ items, page, size, totalItems, totalPages }` because of pagination. Each item still matches the README schema. (ticket 5)
- `days` is stored as-is; the application never advances it. (ticket 5)
- `created_at` is when the row was inserted, not when the Time Deposit was opened. (ticket 5)
- Tables and columns use PostgreSQL's snake_case (`time_deposits.plan_type`, `withdrawals.time_deposit_id`) for the README's `timeDeposits.planType` and `withdrawals.timeDepositId`. The API keeps the README's camelCase names. (ticket 5)
- `planType` is stored and returned as a plain string: no CHECK constraint in the database and no enum in the contract. A Time Deposit with an unknown Plan Type is bad data the application must survive and still list, not reject. (ticket 5)
- A page past the last one returns `200` with an empty `items` list, not an error. (ticket 5)
- A Time Deposit's Withdrawals are listed by `id` ascending (the order they were recorded); the README does not specify an order. (ticket 5)
- The request id is the OpenTelemetry trace id (Micrometer Tracing, `spring-boot-starter-opentelemetry`), not an `X-Request-Id`: it is taken from an incoming W3C `traceparent` header or generated, and returned in the `X-Trace-Id` response header. Spring Boot's log pattern prints it on every log line of the request as `[traceId-spanId]`. A malformed `traceparent` is ignored and a new trace is started, so a client cannot inject text into the logs through it. Nothing is exported: metrics, trace and log export over OTLP are switched off. (ticket 6)
- Swagger UI (`/swagger-ui.html`, `/swagger-ui/**`), springdoc's api-docs (`/v3/api-docs`, `/v3/api-docs.yaml`, `/v3/api-docs/**`) and the contract file `/openapi.yaml` are skipped by the access log entirely: no log lines and no `X-Trace-Id`. Every other request is logged, including one to an unknown path. (ticket 6)
- An error that no specific handler maps to a `400` or `409` (for example a database failure) is logged at `ERROR` with its trace id and answered with a `500` Problem Details response whose `detail` is a fixed "An unexpected error occurred.". The exception's message stays in the log, because it can reveal internals such as SQL. (ticket 6)
- When an exception escapes every handler before the response is committed, the access log's end line reports `status=500`, the status the servlet container answers with. If the response was already committed, it reports the status already sent. (ticket 6)
- Every call to `POST /v1/time-deposits/balance-updates` adds a month of interest. Triggering it once a month is the caller's job (e.g. an external scheduler); retry safety is ticket 11. (ticket 7)
- The whole set of Time Deposits fits in memory during a Balance Update. Batching is out of scope. (ticket 7)
- No row locks. Optimistic locking makes concurrent Balance Updates deterministic: each `204` means exactly one month of interest was applied, and the loser returns `409` having applied nothing. See `docs/adr/0001-optimistic-locking-for-the-balance-update.md`. (ticket 7)
- "All or nothing" means all the writes of a Balance Update commit in one transaction. The load and the interest calculation run before it, in no transaction of the Balance Update's own (Spring Data reads in a short read-only transaction of its own), so no transaction or database connection is held while interest is computed; each changed Time Deposit's `version` from the load is checked when it is saved. A Time Deposit that earns no interest is not saved. (ticket 7)
- The "Balance Update finished, N Time Deposits updated" log counts every Time Deposit the Balance Update went through, including those that earned no interest. (ticket 7)
- The domain keeps the Balance in a `Double` (the shared `TimeDeposit` class must not change), which holds 15 significant digits exactly. A Balance below 10^13 keeps every cent when it receives interest; a larger one may lose cents then. A Balance that receives no interest (outside its Eligibility Window, or with an unknown Plan Type) is not saved, so it keeps its stored value exactly, however large. (ticket 7)
- `TimeDepositApplication` (the `@SpringBootApplication` class) stays in `org.ikigaidigital`, which is also the domain package, because moving it would change Spring's component scanning. It is treated as the composition root: the only class in the domain package allowed to depend on Spring, and the one class the ArchUnit domain rule leaves out. No domain class may depend on it. (ticket 8)
- The ArchUnit domain rule is an allow-list (the JDK, the SLF4J API and the domain itself), stricter than the ticket's list of Spring, JPA, web types and adapters. It also keeps the domain off the application layer and off any other library, so adding a library to the domain means changing the rule on purpose. (ticket 8)
- The ticket's "Docker only" prerequisite covers running the application. Running the tests still needs JDK 25 (and Docker for Testcontainers), as in CI. (ticket 9)
- The Docker image build skips the tests: they need a Docker daemon of their own (Testcontainers). They run with `./mvnw verify` locally and in CI. (ticket 9)
- PostgreSQL is not published on a host port. The walkthrough only needs the API, and a published 5432 would clash with a PostgreSQL already running on the host. `docker compose exec postgres psql -U time_deposits` reaches it when needed. (ticket 9)
- The database lives in a named volume, so Balance Updates survive `docker compose down` and `up`. `docker compose down -v` resets to the seed data. (ticket 9)
- The AI write-up is ticket 10, which has not landed yet. The README says so instead of linking to a file that does not exist; the link is added when ticket 10 lands. (ticket 9)
- The original assignment text is kept word for word under `## Assignment`, with its headings one level lower so they nest there. Its title, "Time Deposit Refactoring Kata - Take-Home Assignment", became the README's title, "Time Deposit Refactoring Kata". (ticket 9)
- `Idempotency-Key` values never expire, and a key is global: not scoped to an endpoint or a client, since only `POST /v1/time-deposits/balance-updates` accepts one. See `docs/adr/0002-idempotency-key-for-the-balance-update.md`. (ticket 11)
- An `Idempotency-Key` is up to 255 letters, digits and `._:+/=-`, so it is safe to log; any other value gets a `400` and adds no interest. (ticket 11)
- The IETF Idempotency-Key draft sends the key as a Structured Field string in double quotes, while most clients send it bare. Both forms are accepted and name the same key: `"abc"` and `abc` are one key. (ticket 11)
- Two calls with the same `Idempotency-Key` that overlap may both load the Time Deposits and compute the interest, but only one saves it. The other waits only while the first one's write transaction is open: if that committed, the waiting call is a replay and returns `204`; if it failed, the waiting call saves its own Balance Update. The IETF draft suggests answering `409` instead. (ticket 11)

## Assignment

### XA Bank Time Deposit

#### Context
A junior developer implemented domain logic for a time deposit system but did not complete the API functionality. Your task is to refactor the existing codebase to implement all required functionalities based on the provided business requirements, ensuring no breaking changes occur.

#### Requirements

1. **API Endpoints**:
    - Create a RESTful API endpoint to update the balances of all time deposits in the database.
    - Create a RESTful API endpoint to retrieve all time deposits.
        - The GET endpoint should return a list of all time deposits with the following schema:
            - `id`
            - `planType`
            - `balance`
            - `days`
            - `withdrawals`

2. **Database Setup**:
    - Store all time deposit plans in a database.
    - Define the following tables:
        - `timeDeposits`:
            - `id`: Integer (primary key)
            - `planType`: String (required)
            - `days`: Integer (required)
            - `balance`: Decimal (required)
        - `withdrawals`:
            - `id`: Integer (primary key)
            - `timeDepositId`: Integer (foreign key, required)
            - `amount`: Decimal (required)
            - `date`: Date (required)

3. **Interest Calculation**:
    - Implement logic to calculate monthly interest based on the plan type:
        - **Basic Plan**: 1% interest
        - **Student Plan**: 3% interest (no interest after 1 year)
        - **Premium Plan**: 5% interest (interest starts after 45 days)
    - No interest is applied for the first 30 days for any existing plans.

4. **Refactoring Constraints**:
    - Do not introduce breaking changes to the shared `TimeDeposit` class or modify the `updateBalance` method signature.
    - Ensure the design is extensible to accommodate future complexities in interest calculations.

5. **Code Quality**:
    - Adhere to SOLID principles, design patterns, and clean code practices where applicable.

6. **AI-Assisted Development**:
    - Set up an AI harness or agent workflow and use it throughout the development for this take-home exercise.
    - Briefly document the tools and setup used (e.g., LLMs, coding assistants, agentic frameworks, configuration).
    - Ensure your AI setup is practical and reproducible.
    - Include any custom rules, system prompts, or agent configurations used.
    - Include a brief summary of which parts of the solution were AI-assisted and why.

#### Important Guidelines
- The existing `TimeDepositCalculator.updateBalance` method is functioning correctly. Ensure its behavior remains unchanged after refactoring.
- The final solution must include **exactly two API endpoints**. Do not develop additional endpoints.
- **Do not** create a pull request or a new branch in the ikigai-digital repository. Instead, fork the repository into your own GitHub repository and develop the solution there.
- Handling invalid input or exceptions is not required.
- Use any tools, frameworks, or libraries you find suitable.
- In case of ambiguity, make logical assumptions and justify them in code comments.

#### Preferred Stack
- Use an OpenAPI Swagger contract.
- Embrace Hexagonal Architecture.
- Follow atomic commit practices.
- Utilize testcontainers.
- Leverage AI-assisted development tools for code generation, testing, and refactoring.

#### Submission Instructions
- Provide clear instructions on how to trigger the endpoints using the Swagger contract.
- Email the link to your public GitHub repository.