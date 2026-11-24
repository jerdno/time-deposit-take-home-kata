## Solution

### Package layout

The code follows a hexagonal layout, and ArchUnit tests (`HexagonalArchitectureTest`) fail `./mvnw verify` when it is broken. The domain is `org.ikigaidigital` itself (`TimeDeposit`, `TimeDepositCalculator`, not its sub-packages) plus `org.ikigaidigital.plantype` (the Plan Types and `PlanTypeResolver`); it depends only on the JDK, the SLF4J API and itself. `org.ikigaidigital.application` holds the use cases, the in- and out-ports and the model they exchange (`application.model`), and may use Spring and the domain but no adapter. `org.ikigaidigital.adapter.web` (generated API interfaces, controller, the Problem Details handler in `web.exception`, web infrastructure) and `org.ikigaidigital.adapter.persistence` (out-port implementations, with the JPA entities in `persistence.entity` and the Spring Data repositories in `persistence.repository`) depend on the application layer and never on each other.

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
- `TimeDepositApplication` (the `@SpringBootApplication` class) stays in `org.ikigaidigital`, which is also the domain package, because moving it would change Spring's component scanning. It is treated as the composition root: the only class in the domain package allowed to depend on Spring, and the one class the ArchUnit domain rule leaves out. No domain class may depend on it. (ticket 8)
- The ArchUnit domain rule is an allow-list (the JDK, the SLF4J API and the domain itself), stricter than the ticket's list of Spring, JPA, web types and adapters. It also keeps the domain off the application layer and off any other library, so adding a library to the domain means changing the rule on purpose. (ticket 8)

# Time Deposit Refactoring Kata - Take-Home Assignment

## XA Bank Time Deposit

### Context
A junior developer implemented domain logic for a time deposit system but did not complete the API functionality. Your task is to refactor the existing codebase to implement all required functionalities based on the provided business requirements, ensuring no breaking changes occur.

### Requirements

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

### Important Guidelines
- The existing `TimeDepositCalculator.updateBalance` method is functioning correctly. Ensure its behavior remains unchanged after refactoring.
- The final solution must include **exactly two API endpoints**. Do not develop additional endpoints.
- **Do not** create a pull request or a new branch in the ikigai-digital repository. Instead, fork the repository into your own GitHub repository and develop the solution there.
- Handling invalid input or exceptions is not required.
- Use any tools, frameworks, or libraries you find suitable.
- In case of ambiguity, make logical assumptions and justify them in code comments.

### Preferred Stack
- Use an OpenAPI Swagger contract.
- Embrace Hexagonal Architecture.
- Follow atomic commit practices.
- Utilize testcontainers.
- Leverage AI-assisted development tools for code generation, testing, and refactoring.

### Submission Instructions
- Provide clear instructions on how to trigger the endpoints using the Swagger contract.
- Email the link to your public GitHub repository.