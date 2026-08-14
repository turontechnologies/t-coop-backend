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
- **Cloudinary** for signed file uploads (profile photos etc.) — deliberately
  *not* using Lombok (its annotation processor silently failed to generate
  code on a newer local JDK during scaffolding — a bad failure mode for a
  shared repo where people will have different JDK versions). Plain
  constructors / Java records instead.

## Prerequisites

- JDK 21+
- Access to the shared MSSQL database (connection details + your IP
  allow-listed on the firewall — ask the team for these)

## Running locally

1. Copy `.env.example` to `.env` and fill in the real `DB_URL` /
   `DB_USERNAME` / `DB_PASSWORD` (get these from the team — don't invent
   your own database).
2. Export those as real environment variables before running (Spring Boot
   doesn't read `.env` files itself):
   - **PowerShell**: `Get-Content .env | ForEach-Object { if ($_ -match '^(\w+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }`
   - **bash/zsh**: `export $(grep -v '^#' .env | xargs)`
   - Or just set them in your IDE's run configuration.
3. Run the app:
   - Windows: `mvnw.cmd spring-boot:run`
   - macOS/Linux: `./mvnw spring-boot:run`
4. Confirm it's up: `GET http://localhost:8080/api/health` should return
   `{"status":"ok",...}`.

Cloudinary uploads need three more env vars (`CLOUDINARY_CLOUD_NAME`,
`CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`) — see `.env.example`. These
were moved here from the frontend's `.env.local`, which is where they used
to live before this backend existed.

## Project layout

```
src/main/java/com/turontechnologies/tcoop/
  TCoopBackendApplication.java   entry point
  config/                        cross-cutting config (CORS, Cloudinary, security, …)
  health/                        liveness check
  upload/                        Cloudinary-backed file uploads (POST /api/uploads)
  (one package per domain area goes here as it's built: cooperative,
   member, savings, loan, notice, auth, …)
src/main/resources/
  application.yml                config (reads DB creds etc. from env vars)
  db/migration/                  Flyway migration scripts
```

## Status

Scaffold + first real endpoint — boots, connects to the database, exposes a
health check, and has a working Cloudinary upload endpoint mirroring the
frontend's existing `/api/upload` route.

**The frontend hasn't been switched over yet.** It still uses its own
Cloudinary keys and its own `/api/upload` route — on purpose, since this
backend isn't deployed anywhere reachable from the live Vercel site yet.
Once it is, the cutover is: point the frontend's upload call at
`POST {this API's URL}/api/uploads`, remove `CLOUDINARY_*` from the
frontend's `.env.local` / Vercel project settings, delete
`src/app/api/upload/route.ts`. Do that in one step, not gradually, so
uploads are never broken in between.

Real domain endpoints (co-operatives, savings, loans, …) come next, one
area at a time, alongside the matching schema migrations.
