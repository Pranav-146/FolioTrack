# User stories

Stories for the FolioTrack board, each matching what was built. Points use the Fibonacci scale (1, 2, 3, 5, 8). **Total: 34 points.**

| # | Story | Points |
|---|---|---|
| 1 | [View portfolio and P&L](#1-view-portfolio-and-pl) | 3 |
| 2 | [Buy at market](#2-buy-at-market) | 5 |
| 3 | [Sell at market](#3-sell-at-market) | 3 |
| 4 | [Safe concurrent orders](#4-safe-concurrent-orders) | 5 |
| 5 | [Order and transaction history](#5-order-and-transaction-history) | 3 |
| 6 | [Simulate a price move](#6-simulate-a-price-move) | 2 |
| 7 | [Ask your portfolio](#7-ask-your-portfolio) | 8 |
| 8 | [Web dashboard](#8-web-dashboard) | 5 |

---

### 1. View portfolio and P&L
**As an** investor, **I want** to see my cash, holdings and unrealized P&L, **so that** I know what my portfolio is worth right now.

- `GET /api/users/{id}/portfolio` returns cash, plus one row per holding with quantity, average cost, current price, market value, cost basis and unrealized P&L.
- It also returns totals for market value, cost basis, unrealized P&L and overall value (cash + market value).
- All money is computed with `BigDecimal` and returned with 2 decimals.
- An unknown user returns `404` with the standard error JSON.

### 2. Buy at market
**As an** investor, **I want** to buy shares at the current price, **so that** I can open or add to a position.

- `POST /api/users/{id}/orders` with `{symbol, side: "BUY", quantity}` executes at the instrument's current price.
- On success: cash goes down by price × quantity, the holding is created or increased, and the average cost is recomputed as a weighted average. A `FILLED` order and a transaction are recorded.
- If cash is insufficient, the order is stored as `REJECTED` with a reason, and nothing else changes.
- Both outcomes return `201` (an order resource was created either way).
- `quantity <= 0` or a missing field returns `400`. An unknown symbol returns `404`.

### 3. Sell at market
**As an** investor, **I want** to sell shares at the current price, **so that** I can take profits or cut losses.

- On success, cash goes up by price × quantity and the holding shrinks. A holding that reaches 0 is deleted.
- Selling more than I hold (or a stock I don't hold) is stored as `REJECTED` with a reason.
- A `FILLED` order and a transaction are recorded. A rejected order writes no transaction.

### 4. Safe concurrent orders
**As an** investor, **I want** simultaneous orders to be processed safely, **so that** I can never spend more cash or sell more shares than I have.

- An order runs in one `@Transactional` method. The user row is read with a pessimistic write lock (`SELECT … FOR UPDATE`), so concurrent orders for one user run one after another.
- If any step fails mid-order, nothing from that order is persisted.
- Verified by `OrderServiceTest` (lock is used) and the manual scripts `scripts/concurrency-check.sh` (20 parallel BUYs, no overspend) and `scripts/rollback-check.sh` (forced failure, state unchanged).

### 5. Order and transaction history
**As an** investor, **I want** to page through my orders and transactions, **so that** I can review what happened.

- `GET /api/users/{id}/orders` and `GET /api/users/{id}/transactions` are paginated (`page` default 0, `size` default 20, max 100) and sorted newest first.
- Orders include side, quantity, execution price, status and rejection reason.
- Invalid paging values return `400`.

### 6. Simulate a price move
**As a** demo user, **I want** to change an instrument's price, **so that** I can see P&L move.

- `GET /api/instruments` lists all instruments with current prices.
- `PUT /api/instruments/{symbol}/price` with `{price}` updates it. The price must be ≥ 0.01 with at most 2 decimals, otherwise `400`. An unknown symbol returns `404`.

### 7. Ask your portfolio
**As an** investor, **I want** to ask questions about my portfolio in plain English, **so that** I get answers without writing queries. My data, and only my data, should be used.

- `POST /api/users/{id}/ask` with `{question}` (1–500 chars) returns the generated SQL, the row count and a plain-English answer.
- The LLM only sees the schema of the user-scoped views (`v_my_account`, `v_my_holdings`, `v_my_orders`, `v_my_transactions`). The prompt is a versioned resource file.
- Generated SQL is validated before running: a single `SELECT`, no comments/semicolons, no write/DDL keywords or dangerous functions, and only allowlisted views. It is wrapped in `LIMIT 100`. Anything else returns `422` and is never executed.
- Queries run on a separate read-only DB role, in a read-only transaction with `SET LOCAL app.user_id` and a 5 s statement timeout.
- If the LLM is unreachable or not configured, the endpoint returns `503`.

### 8. Web dashboard
**As an** investor, **I want** a simple web page, **so that** I can use the app without Postman.

- A React (Vite) page has a user selector, a portfolio table with gains in green and losses in red, an order form showing filled/rejected results, and an ask box.
- API errors are shown using the backend's error message.
- The frontend only formats money and never does arithmetic on it.
- The backend allows CORS only from the dev server origin.
