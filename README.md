# GuardianGrow

Parent-Controlled Learning Platform (Parent / Child / Admin portals).

## Frontend

Stack: Vue 3 + TypeScript + Vite + PrimeVue + Vue Router + Pinia (auth/UI) + TanStack Query (server state).

### Run locally

1) Copy env template:

- `frontend/.env.example` → `frontend/.env`

2) Start dev server:

- `cd frontend`
- `npm install`
- `npm run dev`

Env:

- `VITE_API_BASE_URL` (default: `/api/v1`)

## Backend

Stack: Kotlin + Ktor + jOOQ (SQL-first) + Flyway migrations + Oracle.

### Modules

- `backend/app`: Ktor REST API (auth/RBAC, endpoints)
- `backend/data-jooq`: DB pool + jOOQ DSLContext + Flyway bootstrap
- `backend/db-migrations`: Flyway SQL migrations (resources)
- `backend/rules-engine`: Java rules engine (deterministic session enforcement)
- `backend/reporting-engine`: Java reporting engine (weekly report/PDF later)

### Prereqs

- Java 8+ (project targets Java 8 for compatibility)
- Gradle (recommended) or import as a Gradle project in IntelliJ
- Oracle DB (local XE is fine for development)

### Run locally

From `backend/`:

- If you have Gradle installed: `gradle :app:run`
- If you add a Gradle wrapper later: `./gradlew :app:run`

Config is in `backend/app/src/main/resources/application.conf`.

Env:

- `GG_JWT_SECRET` (JWT HMAC secret; defaults to a dev value if not set)
