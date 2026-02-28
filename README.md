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

- Java 17+
- Gradle (optional; wrapper included) or import as a Gradle project in IntelliJ
- Oracle DB (local XE is fine for development)

### Run locally

From `backend/`:

- Using the included Gradle wrapper (recommended):
	- Windows (PowerShell): `./gradlew.bat :app:run`
	- macOS/Linux: `./gradlew :app:run`
- If you have Gradle installed: `gradle :app:run`

Copy/paste (Windows PowerShell):

```powershell
cd backend
.\gradlew.bat :app:run
```

From the repo root, you can also run:

- Windows (PowerShell): `./gradlew.bat :app:run`
- macOS/Linux: `./gradlew :app:run`

Health check (Windows PowerShell):

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health
```

Config is in `backend/app/src/main/resources/application.conf`.

If you don’t have Oracle running yet, you can start the API without DB by setting:

- `db.enabled = false`

Env:

- `GG_JWT_SECRET` (JWT HMAC secret; defaults to a dev value if not set)
