# Time Deposit Refactoring Kata

A REST API over XA Bank's Time Deposits. It lists them, and it runs a Balance Update that adds one Monthly Interest to every Time Deposit. It is built with Spring Boot 4 on Java 25 and PostgreSQL, from a contract-first OpenAPI definition, in a hexagonal layout. [`GLOSSARY.md`](GLOSSARY.md) defines the domain terms.

For a review, in this order:

1. [Run the service](#run-the-service) with one `docker compose` command.
2. [Try the two endpoints in Swagger UI](#try-the-endpoints-in-swagger-ui).
3. Read the [decisions and assumptions](#decisions-and-assumptions) I made where the assignment left room.
4. Read how I built the solution with an [AI coding agent](#ai-assisted-development).

The original assignment is kept [at the end](#assignment).

## Run the service

You need Docker with Docker Compose v2 (Docker Desktop includes both) and a free port 8080. The application image is built inside Docker, so you need no JDK and no Maven.

From the repository root:

```sh
docker compose up --build
```

This builds the application image, starts PostgreSQL, and starts the application with the `local` profile. On startup Flyway creates the schema and loads ten seeded Time Deposits. The first build downloads Maven and the dependencies, which takes a few minutes. The API is ready when the log shows `Started TimeDepositApplication`.

- To stop, press `Ctrl+C` or run `docker compose down`. The data stays in the `postgres-data` volume, so Balance Updates survive a restart.
- To start again from the seed data, run `docker compose down -v`, then `docker compose up --build`.

## Try the endpoints in Swagger UI

Open <http://localhost:8080/swagger-ui.html>. It shows the contract as written in `java/src/main/resources/openapi.yaml` (also served at <http://localhost:8080/openapi.yaml>), with its two operations. For each step, expand the operation, click "Try it out", then "Execute".

1. `GET /v1/time-deposits` returns `200` and the first page of Time Deposits, ordered by `id`. The page holds the ten seeded Time Deposits with their Withdrawals, and `totalItems` is 10.
2. `POST /v1/time-deposits/balance-updates`, with the optional `Idempotency-Key` field left empty, returns `204` with no body. It added one Monthly Interest to the Balance of every Time Deposit.
3. `GET /v1/time-deposits` again. A Time Deposit inside its Plan Type's Eligibility Window grew by one Monthly Interest (the annual rate divided by twelve, applied to the Balance, rounded to the cent). Every other Balance is unchanged, and Days never changes.

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

Every POST without an `Idempotency-Key` adds another month, so a second POST grows the same Balances again.

To see a safe retry, type a value such as `april-2026` into the `Idempotency-Key` field and click "Execute" twice. Both calls return `204`, and `GET /v1/time-deposits` shows that only the first one added interest. Keys never expire and are kept in the `postgres-data` volume, so pick a value you have not used yet, or start again from the seed data.

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

## Tests and code layout

The tests need JDK 25 and a running Docker daemon, because the integration tests start PostgreSQL with Testcontainers.

```sh
cd java
./mvnw verify
```

`verify` runs the unit, integration and ArchUnit tests and checks the formatting with Spotless (`./mvnw spotless:apply` fixes it). CI (`.github/workflows/ci.yml`) runs the same command on every push and pull request.

The code follows a hexagonal layout:

| Package | Role |
|---|---|
| `org.ikigaidigital` | Domain: `TimeDeposit` and `TimeDepositCalculator`. |
| `org.ikigaidigital.plantype` | Domain: the Plan Types and `PlanTypeResolver`. |
| `org.ikigaidigital.application` | The use cases, the in- and out-ports, and the model they exchange. |
| `org.ikigaidigital.adapter.web` | The API interfaces generated from the contract, the controller, the error handler and the access log. |
| `org.ikigaidigital.adapter.persistence` | The out-port implementations, the JPA entities and the Spring Data repositories. |

- The domain depends only on the JDK, the SLF4J API and itself.
- The application layer may use Spring and the domain, and no adapter.
- The two adapters depend on the application layer and never on each other.

ArchUnit tests (`HexagonalArchitectureTest`) fail `./mvnw verify` when the code breaks one of these rules.

The domain sources have their own source root, `java/src/main/domain`, with their tests in `java/src/test/domain`. Everything else is in `java/src/main/java`. `TimeDepositApplication`, the Spring Boot entry point, also sits in `org.ikigaidigital` and is the one class there that may use Spring.

## Decisions and assumptions

These are my readings and choices where the assignment left room. The two larger decisions each have an ADR in [`docs/adr/`](docs/adr/).

### API

- **GET returns a page, not a bare array.** The assignment asks the GET endpoint for "a list of all time deposits" with five fields. I read that as what each Time Deposit in the response must contain, not as a fixed response format, unlike the constraints the assignment states strictly (no breaking changes, exactly two endpoints). `GET /v1/time-deposits` returns the Time Deposits a page at a time, in an envelope `{ items, page, size, totalItems, totalPages }`, because a response holding every Time Deposit of a bank has no upper size. Each item has exactly the assignment's fields, and paging through `items` reaches all Time Deposits.
- **Every POST adds one month of interest.** The assignment does not say when a Balance Update runs. The application has no schedule and never advances Days, so calling `POST /v1/time-deposits/balance-updates` once a month is the caller's job.
- **The POST takes an optional `Idempotency-Key` header.** The assignment does not ask for it. I added it because the POST changes money and is not safe to repeat. A client whose call timed out cannot tell whether the interest was added, and a retry would add it a second time. With a key, a retry adds nothing. Without a key the endpoint behaves as the assignment describes. See [ADR 0002](docs/adr/0002-idempotency-key-for-the-balance-update.md).
- **Of two Balance Updates that overlap in time, one wins and the other changes nothing.** Without a guard, both would read the old Balances and one would overwrite the other. With optimistic locking, one call returns `204` and applied one month of interest, and the other returns `409 Conflict` and applied nothing. See [ADR 0001](docs/adr/0001-optimistic-locking-for-the-balance-update.md).
- **Error handling stays small.** The assignment says it is not required. The contract declares only what paging and the two decisions above need: `400` for a bad `page`, `size` or `Idempotency-Key`, and `409` for a concurrent Balance Update. Any other failure is a `500` that reveals no internals. Errors are RFC 9457 Problem Details.
- **There are exactly two endpoints.** There is no Spring Boot Actuator and no health endpoint. Swagger UI, `/openapi.yaml` and springdoc's `/v3/api-docs` document the two operations and are not operations themselves.
- **The application logs each API request when it starts and when it ends.** Every log line of a request carries its trace id, and the `X-Trace-Id` response header returns it. Nothing is exported to a collector.

### Interest and Plan Types

- **The original calculator is the specification.** The assignment says `updateBalance` works correctly, so characterization tests pinned its results before any refactoring, edge cases included: half-up rounding of the interest to the cent, a zero Balance, a negative Balance, an unknown Plan Type.
- **The rates are annual.** The assignment lists "1% interest" under monthly interest, while the original code adds the rate divided by twelve each month. I followed the code.
- **The Eligibility Windows come from the original code.** Basic earns interest from day 31, Student from day 31 to day 365 inclusive, Premium from day 46.
- **Each Plan Type is a class.** `BasicPlan`, `StudentPlan` and `PremiumPlan` each own their rate and Eligibility Window, on top of a shared `FixedRatePlanType`. A new Plan Type is one new class and one entry in the list in `TimeDepositCalculator`. A Plan Type with a different formula implements the `PlanType` interface directly.
- **A Time Deposit with an unknown Plan Type is bad data, and the application survives it.** This covers a value such as `gold`, a known value in the wrong case such as `Basic`, and a missing value. Such a Time Deposit earns no interest, is logged at `ERROR`, is still returned by the GET, and does not stop the Balance Update of the others. For that reason `planType` is a plain string, with no CHECK constraint in the database and no enum in the contract. One behaviour differs from the original: with a missing Plan Type the original calculator threw a `NullPointerException` once Days passed 30.
- **"No breaking changes" covers the calculator's constructor too.** The assignment names only `TimeDeposit` and the `updateBalance` signature. `new TimeDepositCalculator()` still works, so existing callers compile unchanged.
- **Withdrawals are history only.** The application stores and returns them, and they play no part in interest, as in the original calculator. No endpoint creates them, since the solution must have exactly two. The seed data has four.

### Data

- **Tables and columns use snake_case.** `time_deposits.plan_type` and `withdrawals.time_deposit_id` stand for the assignment's `timeDeposits.planType` and `withdrawals.timeDepositId`. The API keeps the assignment's camelCase.
- **The schema has three additions.** `time_deposits` has a `version` column for the optimistic locking, both tables have row timestamps, and a third table, `idempotency_keys`, stores the keys.
- **Money is exact in the database and in the API.** The database holds `NUMERIC(19,2)` and the API returns that value unchanged. The domain keeps the Balance in a `Double`, because the shared `TimeDeposit` class must not change. The application rounds a new Balance to the cent when it saves it, and does not write a Time Deposit that earned no interest.
- **A Balance Update is all or nothing and runs in memory.** The application loads every Time Deposit, computes the interest, and saves all new Balances in one transaction. Batching for a very large number of Time Deposits is out of scope.
- **Seed data is for the local run only.** The `local` profile, which `docker compose` uses, loads the ten Time Deposits of the walkthrough. Tests start from the empty schema.

## AI-assisted development

An AI coding agent, Claude Code, wrote all the code, tests, configuration and documentation in this repository. I wrote no code by hand. I made the decisions, and I reviewed and merged every pull request. I worked this way so that my time went into the decisions and the review, and it works because a small ticket with explicit acceptance criteria makes the agent's output quick to check.

### How the work went

1. I prepared the repository for the agent with `/setup-matt-pocock-skills`. It wrote [`AGENTS.md`](AGENTS.md) and [`docs/agents/`](docs/agents/), which tell the agent where the issues, the glossary and the ADRs live.
2. I designed the solution in an interview with `/grill-with-docs`. The agent asked one design question at a time and I answered. It wrote the agreed terms into [`GLOSSARY.md`](GLOSSARY.md) and the two larger decisions into ADRs.
3. The agent wrote the design up as tickets, one [GitHub issue](https://github.com/jerdno/time-deposit-take-home-kata/issues?q=is%3Aissue) each.
4. The agent implemented one ticket at a time, each on its own branch and in its own [pull request](https://github.com/jerdno/time-deposit-take-home-kata/pulls?q=is%3Apr).
5. `/code-review` checked each pull request against the repository's rules and against its ticket. Then I read it, and merged it or sent it back.
6. At the end, `/demonstrate` started the application with `docker compose` and tested both endpoints through Swagger UI and `curl`, the way a tester would.
7. A fresh agent session compared the finished repository with the assignment text. That led to the last code change and to this README.

### How the tickets were set up

- One ticket is one issue, one branch and one pull request. The label `ready-for-agent` marks a ticket the agent can implement, and `ready-for-human` one that needs me.
- Every ticket has the same parts: the constraints, what to build, the acceptance criteria as a checklist of tests, and the assumptions to record. The decisions from the interview are already in the ticket, so the agent implements and does not decide.
- Every ticket opens with the same constraints: use the terms in `GLOSSARY.md`, don't break `TimeDeposit` or the `updateBalance` signature, keep `updateBalance` behaving as it does today, make atomic commits, and record each assumption in a code comment and in this README.
- The order of the tickets protects the existing behaviour:
  1. A Java-only project on the latest stack, with CI.
  2. Characterization tests for `updateBalance`, before any refactoring.
  3. The Plan Type classes.
  4. `GET /v1/time-deposits` end to end: database, persistence, use case, REST, with Testcontainers tests.
  5. `POST /v1/time-deposits/balance-updates` end to end, the same way.
  6. The ArchUnit rules, the access log, and `docker compose` with the README.
  7. The optional idempotency key.
- I squash-merged the pull requests, so `main` has one commit per ticket, and its message lists the commits inside it. The atomic commits themselves are on the `ticket/*` branches.

### Tools, rules and skills

- Claude Code in its desktop app, with GitHub Issues and the `gh` CLI as the issue tracker.
- [`AGENTS.md`](AGENTS.md) holds the standing rules for the agent, and `CLAUDE.md` loads it into Claude Code.
- A skill is a prompt file the agent loads for one kind of task. The skills I used are in [`.claude/skills/`](.claude/skills/), copied from [my fork](https://github.com/jerdno/mp-skills) of [`mattpocock/skills`](https://github.com/mattpocock/skills) (MIT license).

| Skill | What it does | Where I used it |
|---|---|---|
| `setup-matt-pocock-skills` | Configures a repository for the other skills: issue tracker, labels, where the domain docs live. | `AGENTS.md` and `docs/agents/`. |
| `grill-with-docs` | Runs `grilling` and `domain-modeling` together. | The design interview. |
| `grilling` | Interviews me, one question at a time, until the plan has no open decision left. | The design decisions. |
| `domain-modeling` | Keeps the glossary and the ADRs up to date while the design is discussed. | `GLOSSARY.md` and both ADRs. |
| `writing-for-agents` | Guidance for writing a document that an agent will read. | The tickets and `AGENTS.md`. |
| `code-review` | Reviews a change in two parallel sub-agents, one against the repository's standards and one against the ticket. | Every ticket's pull request. |
| `pr` | A pull request template: summary, evidence, merge risk. | The pull request descriptions. |
| `demonstrate` | Tests a change like a human tester: a fresh sub-agent runs the application and drives its real UI or API. | The end-to-end check of both endpoints. |
| `writing-for-humans` | Edits a text for its reader: what we know, what we assume, and nothing the reader does not need. | This README. |

### Reproducing the setup

1. Install [Claude Code](https://claude.com/claude-code) and the GitHub CLI `gh`, and clone this repository.
2. Start Claude Code in the repository root. It loads `AGENTS.md` through `CLAUDE.md`, and every skill in `.claude/skills/`. Nothing else needs to be installed.
3. Work the same way: run `/grill-with-docs` on an idea, have the agent write the outcome up as issues, ask it to implement an issue labelled `ready-for-agent`, then run `/code-review` and `/demonstrate` on the result.

The issue tracker configuration in `docs/agents/issue-tracker.md` points at this fork. Change it to use the skills in another repository.

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