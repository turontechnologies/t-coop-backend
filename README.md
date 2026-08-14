# t-coop-backend

Backend service for T-Coop — a savings and loan platform for corporate
bodies. Java (Spring Boot) + MSSQL (Azure SQL), designed to be called by the
frontend at [t-coop-app](https://github.com/turontechnologies/t-coop-app)
(deployed on Vercel).

## Stack

- **Java 21** + **Spring Boot 3.3**
- **Spring Data JPA** (Hibernate) for persistence
- **MSSQL** (Azure SQL Database) via the official `mssql-jdbc` driver
- **Flyway** for schema migrations — every table/column change is a
  versioned SQL file in `src/main/resources/db/migration`, applied
  automatically on startup. Nobody edits the database by hand.
- **Maven**, via the included wrapper (`./mvnw` / `mvnw.cmd`) — no local
  Maven install required.
- **Spring Security + JWT** (`jjwt`) for auth — stateless, bearer tokens.
- **Cloudinary** for signed file uploads (profile photos etc.) — deliberately
  *not* using Lombok (its annotation processor silently failed to generate
  code on a newer local JDK during scaffolding — a bad failure mode for a
  shared repo where people will have different JDK versions). Plain
  constructors / Java records instead.
- **Docker + Docker Compose** for local development (app + its own SQL
  Server, no Azure needed to work day-to-day) — see
  [`documentation/deployment.md`](documentation/deployment.md).

## Quick start (Docker — fastest way to run everything)

```bash
cp .env.example .env   # fill in JWT_SECRET at minimum: openssl rand -base64 32
docker compose up -d --build
curl http://localhost:8080/api/health
```

That's it — the app and its own SQL Server both come up, Flyway creates
the schema and seeds three demo accounts automatically. See
[`documentation/deployment.md`](documentation/deployment.md) for details,
including how to expose this publicly with a Cloudflare tunnel while Azure
access is pending.

Demo accounts (password `admin123` for all three, same IDs as the
frontend's mock users):

| ID | Role |
|---|---|
| `SA-0001` | super_admin |
| `AD-0001` | admin |
| `MB-0001` | member |

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"membershipId":"AD-0001","password":"admin123"}'
```

## Running without Docker (against Azure, once provisioned)

1. Copy `.env.example` to `.env` and fill in the real `DB_URL` /
   `DB_USERNAME` / `DB_PASSWORD` for the Azure SQL instance (get these
   from the team — don't invent your own database).
2. Export those as real environment variables before running (Spring Boot
   doesn't read `.env` files itself):
   - **PowerShell**: `Get-Content .env | ForEach-Object { if ($_ -match '^(\w+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }`
   - **bash/zsh**: `export $(grep -v '^#' .env | xargs)`
   - Or just set them in your IDE's run configuration.
3. Run the app:
   - Windows: `mvnw.cmd spring-boot:run`
   - macOS/Linux: `./mvnw spring-boot:run`
4. Confirm it's up: `GET http://localhost:8080/api/health` should return
   `{"status":"ok",...}`. Flyway creates the full schema automatically —
   nothing to run by hand.

Cloudinary uploads need three more env vars (`CLOUDINARY_CLOUD_NAME`,
`CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`) — see `.env.example`. These
were moved here from the frontend's `.env.local`, which is where they used
to live before this backend existed.

## Project layout

```
src/main/java/com/turontechnologies/tcoop/
  TCoopBackendApplication.java   entry point
  config/                        cross-cutting config (CORS, Cloudinary, security, …)
  auth/                          JWT issuing/validation, login, /auth/me
  member/                        Member entity + repository
  health/                        liveness check
  upload/                        Cloudinary-backed file uploads (POST /api/v1/uploads)
  (one package per domain area goes here as it's built: cooperative,
   savings, loan, notice, …)
src/main/resources/
  application.yml                config (reads DB creds etc. from env vars)
  db/migration/                  Flyway migration scripts
    V1__init_schema.sql            full baseline schema
    V2__seed_demo_users.sql        SA-0001/AD-0001/MB-0001 demo accounts
Dockerfile                       multi-stage build (Maven -> slim JRE)
docker-compose.yml                app + its own SQL Server, for local dev
```

## Documentation

- [`documentation/api-contracts.md`](documentation/api-contracts.md) — the
  full endpoint spec this backend implements (copied from the frontend
  repo, which is the source of truth for what the UI expects).
- [`documentation/schema-design.md`](documentation/schema-design.md) — the
  database schema and the reasoning behind it.
- [`documentation/api-conventions.md`](documentation/api-conventions.md) —
  base path, auth header, error shape, money/date formatting — read this
  before adding a new endpoint.
- [`documentation/deployment.md`](documentation/deployment.md) — the local
  Docker stack, the temporary public tunnel, and where real Azure
  deployment fits in.

## Status

- [x] Project scaffold (Spring Boot + MSSQL + Flyway + Maven wrapper)
- [x] Health check (`GET /api/health`)
- [x] CORS configured and verified for both `localhost:3000` and the live
      Vercel frontend (`https://t-coop-app.vercel.app`)
- [x] Full baseline database schema (`V1__init_schema.sql`) — proven to
      apply cleanly against a real SQL Server, not just written
- [x] Cloudinary upload endpoint (`POST /api/v1/uploads`), mirroring the
      frontend's existing `/api/upload` route
- [x] Auth — `POST /api/v1/auth/login` (JWT), `GET /api/v1/auth/me`,
      bcrypt password hashing, three seeded demo accounts matching the
      frontend's mock users
- [x] Dockerized (app + DB via `docker-compose.yml`), temporarily exposed
      publicly via a Cloudflare quick tunnel while Azure access is pending
      — see `documentation/deployment.md` for the honest limits of that
- [ ] Azure SQL instance actually provisioned (blocked on Azure login —
      see the team for the connection details once it exists)
- [ ] Real Azure deployment (App Service or similar) — the Docker tunnel
      is a stopgap, not the destination
- [ ] Real domain endpoints (co-operatives, members, savings, loans,
      notices, subscriptions) — one area at a time, per
      `documentation/api-contracts.md`

**The frontend hasn't been switched over to anything here yet.** It still
uses its own Cloudinary keys and its own `/api/upload` route. Once the
backend has a stable URL (real Azure deployment, not the tunnel), the
cutover is: point the frontend's upload call at
`POST {this API's URL}/api/v1/uploads`, remove `CLOUDINARY_*` from the
frontend's `.env.local` / Vercel project settings, delete
`src/app/api/upload/route.ts`. Do that in one step, not gradually, so
uploads are never broken in between. Same one-step-cutover rule applies to
every other feature as its real endpoint goes live — auth included.
