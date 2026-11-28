# FinSight — Personal Finance, Clarified

FinSight is a full-stack personal finance dashboard: multi-account balances, transaction search, monthly budgets, savings goals, recurring payments, live FX conversion, and a Python analytics service that forecasts spending, flags anomalies and auto-categorizes transactions.

> **Demo account:** `demo@finsight.app` / `Demo@1234` — seeded with 12 months of realistic transactions, budgets, goals and one deliberately suspicious laptop purchase.

![Dashboard](docs/screenshots/dashboard.png)

## Table of contents

- [Live demo](#live-demo)
- [Features](#features)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Repository layout](#repository-layout)
- [Screenshots](#screenshots)
- [Local development](#local-development)
- [Environment variables](#environment-variables)
- [API reference](#api-reference)
- [Database](#database)
- [Tests](#tests)
- [Deployment](#deployment)
- [Design decisions](#design-decisions)
- [Skills demonstrated](#skills-demonstrated)
- [Known limitations](#known-limitations)

## Live demo

| Service | URL |
| --- | --- |
| Frontend | https://finsight-alpha-lime.vercel.app |
| REST API | https://finsight-api-0878.onrender.com |
| Swagger UI | https://finsight-api-0878.onrender.com/swagger-ui.html |
| FastAPI docs | https://finsight-insights-bhra.onrender.com/docs |

The deployment uses free tiers. A keep-alive workflow holds the API warm for most of the day, the client starts waking it from an inline script before the JS bundle loads, and the dashboard paints its last cached payload instantly before refreshing in the background.

## Features

**Accounts & transactions**
- Multiple account types (checking, savings, credit, cash, investment) with computed running balances
- Full transaction CRUD with category, merchant, notes, date and currency
- Server-side filtering by type, account, category, date range, amount range and free-text search
- Sorting and pagination (Spring Data `Specification` + `Pageable`)
- CSV export of the current filter and CSV import with duplicate detection and row-level error reporting
- Multi-currency entries converted to the user’s base currency using live Frankfurter rates, cached in memory with an offline fallback

**Budgets & goals**
- Monthly per-category budgets with live spent/remaining totals, progress bars and over-budget warnings
- Savings goals with contributions, target dates and completion states

**Recurring transactions**
- Daily/weekly/monthly schedules with a Spring `@Scheduled` sweep at 06:00 UTC
- Manual “Run due now” action for demos; catch-up posts and next-date advancement

**Dashboard**
- Total balance, monthly income/expense, net and savings rate
- Income-vs-expense area chart (6 months), category donut, top-category bar chart
- Budget alerts and the eight most recent transactions

**AI insights (Python)**
- Per-category next-month spend forecast (linear regression with confidence, recency-weighted fallback)
- Anomaly detection using per-category z-scores (severity + explanation)
- Plain-language month-over-month insights
- TF-IDF + logistic-regression auto-categorization, exposed inline in the transaction form
- Graceful degradation: if the analytics service sleeps, the dashboard still works and the Insights page explains why

**Platform**
- JWT auth with BCrypt password hashing, per-user data isolation on every query
- Bean validation with structured `422` field errors, global exception handling, CORS, Swagger/OpenAPI
- Responsive Tailwind UI with semantic HTML, keyboard-friendly dialogs, loading skeletons, empty/error states and dark mode
- Flyway migrations, Dockerfiles per service, `docker-compose.yml`, Render blueprint, Vercel config

## Architecture

```mermaid
flowchart LR
    U[Browser<br/>React + Vite + Tailwind] -->|HTTPS / JSON| A

    subgraph Render
        A[Spring Boot 3 API<br/>Java 17]
        P[FastAPI Insights<br/>Python 3.11]
        A -->|REST| P
    end

    A -->|JPA / Flyway| D[(Neon PostgreSQL 16)]
    A -->|HTTPS| F[Frankfurter FX API]
    P -->|pandas + scikit-learn| A
    V[Vercel<br/>Static frontend] -.->|serves| U
```

**Request flow for insights:** the React client calls `GET /api/insights/overview`. The Java API aggregates monthly category totals and recent expenses with SQL (`GROUP BY`), posts them to `POST /insights/overview` on FastAPI, then returns a single typed payload to the browser. Python does the modelling; Java owns auth, ownership and persistence.

## Tech stack

| Layer | Technologies |
| --- | --- |
| Frontend | React 18, Vite 6, React Router 6, Tailwind CSS 3.4, Recharts, Lucide icons |
| Backend | Java 17, Spring Boot 3.3, Spring Web, Spring Security (JWT), Spring Data JPA, Bean Validation, Flyway, springdoc OpenAPI, Commons CSV |
| Analytics | Python 3.11, FastAPI, pandas, NumPy, scikit-learn |
| Database | PostgreSQL 16 (Neon in production) |
| Infrastructure | Docker, Docker Compose, Render, Vercel, GitHub |

## Repository layout

```
FinanceWebsite/
├── frontend/                 # React + Vite SPA
│   ├── src/api/              # fetch client with auth + slow-request events
│   ├── src/components/       # layout, dialogs, charts primitives, transaction form
│   ├── src/context/          # auth, theme, toasts
│   ├── src/pages/            # dashboard, transactions, budgets, goals, insights, recurring, accounts
│   └── Dockerfile, vercel.json, nginx.conf
├── backend-java/             # Spring Boot REST API
│   └── src/main/java/com/finsight/api/
│       ├── config/           # JWT, security, CORS, OpenAPI, HTTP clients
│       ├── controller/       # REST endpoints
│       ├── domain/           # JPA entities
│       ├── dto/              # request/response records
│       ├── exception/        # ApiException + global handler
│       ├── repository/       # Spring Data repositories, native GROUP BY reports
│       └── service/          # business logic, FX, insights client, scheduler
├── insights-python/          # FastAPI analytics service
│   ├── app/                  # forecasting, anomalies, insights, categorizer
│   └── tests/
├── db/migration/             # canonical Flyway SQL (schema + demo seed)
├── docs/screenshots/         # README images
├── docker-compose.yml
└── render.yaml
```

## Screenshots

| Login | Dashboard (dark) |
| --- | --- |
| ![Login](docs/screenshots/login.png) | ![Dashboard dark](docs/screenshots/dashboard-dark.png) |

| Transactions | Budgets |
| --- | --- |
| ![Transactions](docs/screenshots/transactions.png) | ![Budgets](docs/screenshots/budgets.png) |

| AI insights | Goals |
| --- | --- |
| ![Insights](docs/screenshots/insights.png) | ![Goals](docs/screenshots/goals.png) |

| Accounts & FX | Recurring |
| --- | --- |
| ![Accounts](docs/screenshots/accounts.png) | ![Recurring](docs/screenshots/recurring.png) |

| Mobile |
| --- |
| <img src="docs/screenshots/dashboard-mobile.png" alt="Mobile dashboard" width="280" /> |

## Local development

### Prerequisites

- Node.js 20+ and npm
- Java 17+ and Maven 3.9+
- Python 3.11+
- PostgreSQL 16 (or Docker)

### 1. Database

```bash
createdb finsight
# The schema and demo seed apply automatically through Flyway when the API starts.
# To apply manually:
psql -d finsight -f db/migration/V1__schema.sql
psql -d finsight -f db/migration/V2__seed.sql
```

### 2. Python analytics service

```bash
cd insights-python
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --reload --port 8000
# docs at http://localhost:8000/docs
```

### 3. Spring Boot API

```bash
cd backend-java
mvn spring-boot:run
# health:    http://localhost:8080/api/health
# Swagger:   http://localhost:8080/swagger-ui.html
```

Defaults work out of the box for a local PostgreSQL database named `finsight` owned by your OS user with no password. Override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `INSIGHTS_SERVICE_URL`.

A single `DATABASE_URL` (the form Neon and Render hand out) also works — the app parses it into the JDBC URL, username and password at startup, and `DB_URL` wins if both are set. Prefer Neon's direct (unpooled) endpoint there, since HikariCP already pools connections.

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173 — Vite proxies /api to localhost:8080
```

### Everything with Docker

```bash
docker compose up --build
# frontend http://localhost:3000
# API      http://localhost:8080
# insights http://localhost:8000
```

## Environment variables

| Variable | Used by | Example / default |
| --- | --- | --- |
| `DB_URL` | backend | `jdbc:postgresql://host/db?sslmode=require` |
| `DB_USERNAME` / `DB_PASSWORD` | backend | Neon role credentials |
| `DATABASE_URL` | backend | single-URL form (`postgresql://user:pass@host/db?sslmode=require`); used only when `DB_URL` is unset |
| `JWT_SECRET` | backend | random string ≥ 32 bytes |
| `JWT_EXPIRATION_MINUTES` | backend | `10080` (7 days) |
| `CORS_ALLOWED_ORIGINS` | backend | `https://your-app.vercel.app` |
| `INSIGHTS_SERVICE_URL` | backend | `https://finsight-insights.onrender.com` |
| `INSIGHTS_TIMEOUT_SECONDS` | backend | `20` |
| `FRANKFURTER_URL` | backend | `https://api.frankfurter.app` |
| `CURRENCY_CACHE_MINUTES` | backend | `360` |
| `VITE_API_BASE_URL` | frontend (build time) | `https://finsight-api.onrender.com` |
| `PORT` | backend / insights | injected by Render |

See `.env.example` for a copy-paste template. Never commit real secrets.

## API reference

Base path `/api`. All routes except auth, health and currency require `Authorization: Bearer <JWT>`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/auth/register` | Create account + seed default categories/account |
| `POST` | `/auth/login` | Obtain JWT |
| `GET` | `/auth/me` | Current profile |
| `GET` | `/accounts` | Accounts with computed balances |
| `POST` / `PUT` / `DELETE` | `/accounts`, `/accounts/{id}` | Create / update / archive |
| `GET` | `/categories` | List categories |
| `POST` / `PUT` / `DELETE` | `/categories`, `/categories/{id}` | Manage categories |
| `GET` | `/transactions` | Filter + paginate (`type`, `accountId`, `categoryId`, `from`, `to`, `search`, `minAmount`, `maxAmount`, `page`, `size`, `sortBy`, `direction`) |
| `GET` | `/transactions/{id}` | Single transaction |
| `POST` / `PUT` / `DELETE` | `/transactions`, `/transactions/{id}` | Create / update / delete |
| `GET` | `/transactions/export` | CSV of the current filter |
| `POST` | `/transactions/import` | Multipart CSV import |
| `GET` | `/budgets?month=YYYY-MM` | Budgets with live spend |
| `GET` | `/budgets/history?months=6` | Past months: budgeted vs spent, and whether each month was met |
| `POST` / `PUT` / `DELETE` | `/budgets`, `/budgets/{id}` | Manage budgets |
| `GET` | `/goals` | Savings goals |
| `POST` / `PUT` / `DELETE` | `/goals`, `/goals/{id}` | Manage goals |
| `POST` | `/goals/{id}/contribute` | Add funds |
| `GET` | `/recurring` | Scheduled transactions |
| `POST` / `PUT` / `DELETE` | `/recurring`, `/recurring/{id}` | Manage schedules |
| `POST` | `/recurring/run-due` | Post due items immediately |
| `GET` | `/dashboard` | Aggregated dashboard payload |
| `GET` | `/dashboard/cashflow?range=6m` | Income vs expenses for `1w`, `1m`, `3m`, `6m` or `1y` |
| `GET` | `/insights/overview` | Forecast, anomalies, insights |
| `POST` | `/insights/categorize` | Suggest a category for a description |
| `GET` | `/currency/rates?base=USD` | Cached FX rates |
| `GET` | `/currency/supported` | Supported currency codes |
| `GET` | `/api/health` | Liveness probe |

Interactive documentation: **`/swagger-ui.html`** (OpenAPI JSON at `/v3/api-docs`). The Python service documents itself at **`/docs`**.

Example:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@finsight.app","password":"Demo@1234"}'

curl "http://localhost:8080/api/transactions?search=coffee&size=5" \
  -H "Authorization: Bearer $TOKEN"
```

CSV import needs at least `Date, Amount, Description`; optional columns are `Type, Category, Account, Merchant, Currency`. Unknown categories and malformed rows are skipped with per-row messages.

## Database

Seven tables with UUID primary keys, foreign keys, `CHECK` constraints, and indexes tuned for per-user date-range queries:

`users`, `accounts`, `categories`, `transactions`, `budgets`, `recurring_transactions`, `goals`

Two reporting views back the dashboard:

- `monthly_spending_summary` — category totals per user per month
- `monthly_cashflow` — income/expense totals per user per month

Migrations live in `db/migration` and are copied into the API jar by Maven, so Flyway (`classpath:db/migration`) runs the same canonical SQL locally, in Docker and on Neon. The seed is idempotent — it detects the demo user and returns early on re-runs.

## Tests

```bash
cd backend-java && mvn test          # JUnit 5 + Mockito: auth, transactions, budgets, recurrence
cd insights-python && .venv/bin/python -m pytest -q   # fastapi TestClient + model assertions
cd frontend && npm test              # Vitest formatting helpers
```

Tested behaviours include BCrypt login rejection, onboarding seed counts, currency conversion math, category-kind validation, budget over-limit math, month-end recurrence advancement, forecast trend detection, anomaly scoring and category suggestions.

## Deployment

The intended production topology is **Neon + Render (Docker) + Vercel**:

1. **Neon** — create a PostgreSQL project, copy the pooled connection string. Flyway creates and seeds everything on first boot.
2. **Render** — create a Blueprint from `render.yaml`, or two Docker web services:
   - `finsight-api`: Dockerfile `backend-java/Dockerfile`, context repo root, health check `/api/health`, env `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `INSIGHTS_SERVICE_URL`.
   - `finsight-insights`: Dockerfile `insights-python/Dockerfile`, context `insights-python`, health check `/health`.
3. **Vercel** — import `frontend`, framework Vite, build `npm run build`, output `dist`, env `VITE_API_BASE_URL=https://<api>.onrender.com`. `vercel.json` rewrites all routes to `index.html`.
4. Set the API’s `CORS_ALLOWED_ORIGINS` to the final Vercel URL and redeploy.

Free-tier services sleep after inactivity. GitHub throttles a five-minute cron on a quiet repo to a handful of runs a day, so each keep-alive run now pings the API for hours instead of once, keeping the instance (and its database) warm. FX rates are prefetched in the background while the instance boots so the first dashboard render does not wait on the currency API, and the insights page degrades gracefully if the Python service is still waking.

## Design decisions

- **`amount_base` is the single source of truth for aggregates.** Transactions store original `amount`, `currency`, the `exchange_rate` at entry time and the converted `amount_base`; all dashboards, budgets and SQL reports use the base amount so historical totals never drift when FX moves.
- **Flyway migrations live in `/db` and are packaged into the API jar.** One canonical schema for local, Docker and production — no duplicate SQL.
- **UUID keys + `ddl-auto=validate`.** Hibernate validates against Flyway-managed tables instead of mutating them.
- **Filtering via JPA Specifications.** Optional filters compose cleanly without a query-per-combination.
- **JWT carries only the user id.** The API reloads the user per request, so an invalidated account stops working before token expiry.
- **Python is optional at runtime.** Java owns all money math; the analytics service is isolated behind a client that returns `available: false` on failure.
- **Caching at the edge of the FX call.** Frankfurter responses are cached for six hours; if the API is unreachable an approximate table keeps balances usable and is flagged in the UI.

## Skills demonstrated

- **Frontend:** React component architecture, context state, protected routes, responsive Tailwind layouts, dark mode, accessible dialogs/forms, Recharts data visualization, optimistic UX with skeletons, toasts and error recovery.
- **Backend:** layered Spring Boot design (controller/service/repository/DTO), Spring Security JWT filters, Bean Validation, global exception mapping, JPA relationships and Specifications, native `GROUP BY` reporting queries, scheduled jobs, CSV parsing, HTTP client resilience.
- **Database:** relational modelling with constraints and indexes, aggregate views, idempotent seed data, Flyway migrations.
- **Python / data:** FastAPI service design, pandas-shaped aggregations, linear-regression forecasting with confidence, statistical anomaly detection, TF-IDF text classification, pytest.
- **DevOps:** multi-stage Docker builds, Compose orchestration, Render blueprint, Vercel static deployment, environment-based configuration and secret hygiene.

## Known limitations

- Free-tier hosting sleeps; the first load after a long idle is still slower than a warm one.
- Recurrence supports daily, weekly and monthly schedules (no custom RRULEs).
- CSV import matches existing categories by name and caps at 5,000 rows per file.
- FX rates are cached per process; a multi-instance deployment would share no cache.
- JWTs are stateless with no refresh/revocation list; rotating `JWT_SECRET` signs everyone out.
- No bank integrations or receipt uploads; data is entered manually, imported or scheduled.
- The analytics models are intentionally lightweight and explainable — useful signals, not financial advice.

---

Built as a portfolio project demonstrating full-stack engineering across React, Java/Spring Boot, PostgreSQL and Python.
