# FolioTrack — Project Specification

You are helping me build a resume project for a Fidelity Investments software engineering internship interview. I have 2 DAYS. Scope discipline matters more than features. I must be able to explain every line of code in an interview, so explain key design decisions as you go and keep the code clean and idiomatic, not clever.

## About me
- 2nd-year B.Tech CSE student. Comfortable with Python, Go, C++, REST APIs, FastAPI, Docker, SQLite.
- NEW to: Java/Spring Boot, PostgreSQL beyond basics, JUnit/Mockito, CI/CD, SonarCloud, React.
- So: explain Spring Boot concepts briefly when they first appear (dependency injection, @Transactional, JPA, etc.).

## Why this project
The job description emphasizes: Java/OOP, SQL and database concepts, REST web services, SDLC, TDD and test automation, CI/CD (Jenkins/Sonar), Docker, Git/GitHub workflow with user stories, React/JavaScript, and GenAI/prompt writing. Fidelity is a brokerage, so the project is in the finance domain.

## Project: FolioTrack, a Portfolio & Trade Management Platform
A backend-first web app where a user holds cash and stocks, places buy/sell market orders, views holdings and P&L, sees transaction history, and asks natural-language questions about their portfolio through a GenAI feature.

## Tech stack (fixed, do not substitute)
- Java 25 (installed locally; use Temurin 25 in CI), Spring Boot 3.5.x (NOT 4.x), Maven (use the Maven wrapper)
- Spring Web, Spring Data JPA, Spring Validation
- PostgreSQL 16 (Docker), Flyway for schema migrations
- springdoc-openapi (Swagger UI) for API docs
- JUnit 5 + Mockito for unit tests; JaCoCo for coverage
- Docker + Docker Compose (app + db)
- GitHub Actions CI: build, test, JaCoCo report, SonarCloud scan
- LLM API (provider configurable via env var; key never committed)
- Optional on Day 2 only: minimal React (Vite) dashboard

## Domain model / schema (Flyway migrations)
- users (id, name, email, cash_balance NUMERIC(15,2), created_at)
- instruments (id, symbol UNIQUE, name, current_price NUMERIC(12,2), updated_at)
- holdings (id, user_id FK, instrument_id FK, quantity, avg_cost NUMERIC(12,2), UNIQUE(user_id, instrument_id))
- orders (id, user_id FK, instrument_id FK, side BUY/SELL, quantity, price_at_execution, status FILLED/REJECTED, rejection_reason, created_at)
- transactions (id, user_id FK, order_id FK, type, amount, created_at)
- Indexes on orders(user_id, created_at) and transactions(user_id, created_at)
- Seed data migration: 2-3 users, ~10 instruments with prices
- Use NUMERIC/BigDecimal for money everywhere, never double. Be ready to explain why.

## REST API
- GET    /api/users/{id}/portfolio       -> cash, holdings with current value, unrealized P&L per holding and total
- POST   /api/users/{id}/orders          -> body {symbol, side, quantity}; executes market order at current price
- GET    /api/users/{id}/orders          -> order history (paginated)
- GET    /api/users/{id}/transactions    -> transaction history (paginated)
- GET    /api/instruments                -> list instruments and prices
- PUT    /api/instruments/{symbol}/price -> update price (simulates market movement)
- POST   /api/users/{id}/ask             -> body {question}; GenAI feature (see below)
- Proper HTTP status codes, DTOs (never expose entities), Bean Validation on requests, a @RestControllerAdvice global exception handler returning a consistent error JSON.

## Order execution rules (the core business logic)
- Whole order execution runs in ONE @Transactional service method.
- BUY: reject if cash_balance < price * quantity; else deduct cash, upsert holding, recompute weighted avg_cost, insert order (FILLED) + transaction.
- SELL: reject if holding quantity < requested; else add cash, reduce/delete holding, insert order + transaction.
- Rejected orders are still recorded with status REJECTED and a reason.
- Quantity must be > 0. Unknown symbol -> 404.
- Handle concurrent orders on the same user safely (use pessimistic lock on the user row, or optimistic locking with @Version; pick one and explain the tradeoff).
- Demonstrate rollback: if any step fails mid-order, nothing is persisted.

## GenAI feature: "Ask your portfolio" (with security guardrails)
Flow: question -> LLM generates SQL -> validate -> execute read-only -> LLM summarizes result in plain English.
- Prompt template gives the LLM ONLY the schema of user-scoped views (e.g. v_my_holdings, v_my_orders, v_my_transactions) and few-shot examples. Keep the prompt in a versioned resource file.
- The views filter by current_setting('app.user_id'); set it per request with SET LOCAL inside a transaction, so the LLM can never see another user's data.
- Execute using a SEPARATE read-only Postgres role that only has SELECT on those views.
- Validator: single statement only, must start with SELECT, reject ; -- /* and keywords like INSERT/UPDATE/DELETE/DROP/ALTER/GRANT/COPY, only allowlisted views may be referenced, wrap as SELECT * FROM (<query>) q LIMIT 100, statement timeout.
- If validation fails, return a safe error and do not execute.
- Unit-test the validator heavily, including injection attempts.
- LLM client is behind an interface so tests can mock it.

## Testing (TDD where practical)
- Write service-layer tests FIRST for order execution: successful buy, successful sell, insufficient cash, insufficient holdings, avg_cost calculation, unknown symbol, invalid quantity.
- Mockito for repositories and the LLM client.
- Controller tests with @WebMvcTest + MockMvc.
- SQL validator tests (allowed queries, injection attempts, forbidden keywords, non-allowlisted tables).
- Export a Postman collection with tests for the main endpoints into /postman.
- Report the REAL coverage number from JaCoCo. Never invent metrics.

## DevOps
- Multi-stage Dockerfile for the app.
- docker-compose.yml: postgres + app, healthcheck on db, env vars via .env (.env.example committed, .env gitignored).
- GitHub Actions workflow on push/PR: setup Java, mvn verify, JaCoCo, SonarCloud scan (token from repo secrets).
- Include a short note in the README on how this maps to a Jenkins pipeline (stages: checkout, build, test, Sonar quality gate, docker build), since the JD mentions Jenkins.

## Process / GitHub hygiene
- Help me write 6-8 user stories with acceptance criteria and story-point estimates for a GitHub Projects board.
- Small, meaningful commits; feature branches; PRs into main.

## Build plan (follow this order, stop after each phase so I can review and understand before moving on)
DAY 1 - Core backend
  Phase 1: Project skeleton, Docker Compose Postgres, Flyway schema + seed data, app boots, Swagger UI works.
  Phase 2: Entities, repositories, DTOs, instrument + portfolio endpoints.
  Phase 3: Order execution service written test-first (tests then implementation), concurrency handling, order/transaction history endpoints, global exception handler.
  Phase 4: Controller tests, Postman collection.
DAY 2 - JD keywords and polish
  Phase 5: GenAI ask feature: views, read-only role, validator + tests, LLM client interface + implementation, summarization.
  Phase 6: Dockerfile, full docker-compose, GitHub Actions + JaCoCo + SonarCloud.
  Phase 7 (only if time): minimal React dashboard (portfolio table, order form, ask box).
  Phase 8: README (overview, architecture diagram in Mermaid, setup, API table, design decisions, testing, CI, screenshots placeholders), user stories, final cleanup.

## After each phase, give me
1. What was built and the files changed
2. 2-3 key design decisions and WHY (interview-ready explanations)
3. How to run/verify it
4. 2-3 likely interview questions about this phase with short answers

## Constraints
- If something is taking too long, tell me and suggest what to cut. Protect phases 1-6.
- No unnecessary abstractions, no features beyond this spec.
- Never hardcode secrets.
