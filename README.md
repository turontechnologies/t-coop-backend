# t-coop-backend

Backend service for T-Coop — a savings and loan platform for corporate
bodies. Java (Spring Boot) + MSSQL (Azure SQL), designed to be called by the
frontend at [t-coop-app](https://github.com/turontechnologies/t-coop-app)
(deployed on Vercel).

## Daily startup (everything is already built — just bring it back up)

Three things need to be running, in this order, every time the machine
restarts or Docker/ngrok get stopped:

**1. Docker Desktop** — open it normally (from the Start menu / taskbar) and
wait until it says it's running before continuing.

**2. The backend + database containers:**

```bash
cd t-coop-backend
docker compose up -d
curl http://localhost:8080/api/health   # confirm it's up — should return {"status":"ok",...}
```

**3. The ngrok tunnel** (makes the backend reachable from the deployed
Vercel frontend, not just `localhost`):

```bash
ngrok http --url=hamster-probiotic-compile.ngrok-free.dev 8080
```

Leave this running in its own terminal window — closing it drops the
tunnel. Confirm it worked from a *different* terminal:

```bash
curl https://hamster-probiotic-compile.ngrok-free.dev/api/health
```

That's it — no rebuild needed unless the backend's code actually changed
(only then: `docker compose up -d --build`). The frontend's
`NEXT_PUBLIC_API_URL` never needs updating for this, since the ngrok domain
is a **permanent** static domain (unlike a Cloudflare quick tunnel) — it's
tied to the ngrok account, not to this specific run.

If `ngrok.exe` fails to launch or reports a virus warning, that's Windows
Defender flagging it (a known false positive for tunneling tools) — see
`documentation/deployment.md` § 2a for the options (a targeted file
exclusion is safer than turning Defender off entirely).

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
including how to expose this publicly with an ngrok tunnel (or Cloudflare's,
if ngrok isn't available) while Azure access is pending. Already set this
up before? Jump to **Daily startup** above instead.

Demo accounts (password `admin123` for both):

| ID | Role |
|---|---|
| `SA-0001` | super_admin |
| `MB-0001` | member |

There's no standalone demo admin account — a co-op **is** its admin login.
Onboarding a co-op via `POST /cooperatives` creates its admin as a `Member`
row whose `id` equals the co-op's own `id`, password `admin123` by default
(see `documentation/flows.md`'s co-operative onboarding section). `COOP-0001`
(originally seeded by `V2` as a separate `AD-0001` row, renamed onto this
scheme by `V7`) works as a ready-made admin login:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"membershipId":"COOP-0001","password":"admin123"}'
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
  auth/                          JWT issuing/validation, login, /auth/me, /auth/logout
  member/                        Member entity + repository
  cooperative/                   Cooperative entity + repository + controller — full CRUD,
                                  admin provisioning (super admin only)
  savings/                       SavingsType/SavingsRecord entities + repositories
  loan/                          LoanType/LoanRecord entities + repositories
  audit/                         AuditLog entity/service/controller — every login/logout/
                                  mutation, GET /api/v1/audit-log (super admin only)
  dashboard/                     GET /api/v1/dashboard/summary (role-aware aggregates)
  profile/                       GET/PATCH /api/v1/profile (self-service), POST /profile/password
  settings/                      PlatformSettings singleton — GET/PATCH /api/v1/settings/fees,
                                  /settings/collection-account, /settings/integrations (super admin)
  subscription/                  SubscriptionPayment/SubscriptionPaymentIntent/SubscriptionPlan
                                  entities + repos, SubscriptionController (super-admin manual
                                  recording + the admin's self-service /subscriptions/me*
                                  Paystack/Flutterwave checkout), SubscriptionPlanController
                                  (super-admin CRUD on the price list, /settings/subscription-plans),
                                  PaymentGatewayService (real server-side verification),
                                  SubscriptionGateFilter (the platform-wide subscription lock)
  common/                        GlobalExceptionHandler — {"error": "..."} for every endpoint
  health/                        liveness check
  upload/                        Cloudinary-backed file uploads (POST /api/v1/uploads)
  (one package per domain area goes here as it's built: notice, …)
src/main/resources/
  application.yml                config (reads DB creds etc. from env vars)
  db/migration/                  Flyway migration scripts
    V1__init_schema.sql            full baseline schema
    V2__seed_demo_users.sql        SA-0001/AD-0001/MB-0001 demo accounts
    V3__seed_dashboard_data.sql    savings/loan types + records so the dashboard has real numbers
    V4__seed_member_profiles.sql   fills in demo accounts' profile fields (bank, NIN, address, …)
    V5__fix_profile_audit_log_labels.sql   corrects historical audit_log rows to the right module/action
    V6__add_collection_account_and_integrations.sql   adds columns to platform_fee_settings
    V7__admin_login_uses_cooperative_id.sql   renames every admin Member row's id to match its
                                    cooperative_id — a co-op logs in as itself, not a separate AD-XXXX id
    V8__subscriptions.sql          adds subscription_cycle/subscription_expires_at to cooperatives,
                                    type/cycle to subscription_payments
    V9__subscription_payment_intents.sql   the initialize/confirm bridge table for self-service gateway payments
    V10__subscription_payment_resulting_expiry.sql   adds resulting_expires_at (see V11 for why split)
    V11__backfill_subscription_payment_resulting_expiry.sql   backfills it for existing rows
    V12__subscription_plans.sql    the super admin's editable price list, seeded with the original
                                    Weekly/Monthly/Quarterly/Yearly figures as real editable rows
    V13__flexible_subscription_cycle_labels.sql   drops the old fixed-enum CHECK constraints on
                                    cycle/subscription_cycle columns now that a "cycle" is
                                    whatever label a subscription_plans row has
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
- [`documentation/flows.md`](documentation/flows.md) — Mermaid sequence
  diagrams for the auth, dashboard-summary, and profile request flows.

## Status

- [x] Project scaffold (Spring Boot + MSSQL + Flyway + Maven wrapper)
- [x] Health check (`GET /api/health`)
- [x] CORS configured and verified for both `localhost:3000` and the live
      Vercel frontend (`https://t-coop-app.vercel.app`) — wired into the
      Spring Security filter chain itself (not just Spring MVC), so even a
      401 generated directly by Security carries the right headers; see
      `documentation/api-conventions.md` § CORS for why that distinction
      matters
- [x] Full baseline database schema (`V1__init_schema.sql`) — proven to
      apply cleanly against a real SQL Server, not just written
- [x] Cloudinary upload endpoint (`POST /api/v1/uploads`), mirroring the
      frontend's existing `/api/upload` route
- [x] Auth — `POST /api/v1/auth/login` (JWT), `GET /api/v1/auth/me`,
      `POST /api/v1/auth/logout`, bcrypt password hashing, seeded
      super_admin/member demo accounts (a co-op's admin is provisioned by
      onboarding a co-op, not seeded separately — see below), every
      login/logout audit-logged (`audit_log` table); a non-Active member
      gets a distinct 403 "account not active" message instead of the
      generic invalid-credentials one, but only once the password has
      already matched
- [x] Dashboard — `GET /api/v1/dashboard/summary`, role-aware (platform-wide
      for `super_admin`, co-op-scoped for `admin`, personal for `member`),
      cards + recentActivity computed from real `savings_records` /
      `loan_records`; see `documentation/flows.md` for what's real vs.
      illustrative (the hourly chart and dividends figure)
- [x] Profile — `GET`/`PATCH /api/v1/profile` (self-service, any
      authenticated member), server-side validation mirrors the frontend's
      zod schema exactly, every update audit-logged; `POST /api/v1/profile/password`
      for self-service password change (verifies current password, 400 if
      wrong, never a generic 500)
- [x] `GlobalExceptionHandler` — every endpoint now guaranteed to return
      `{"error": "..."}` for validation failures and malformed request
      bodies, not Spring's default error shapes (see `api-conventions.md`)
- [x] Audit log — `GET /api/v1/audit-log` (super admin only, 403 for
      everyone else), latest 200 entries platform-wide with the actor's
      name/role joined in and location resolved from IP (cached, geo-lookup
      only runs on this read — never slows down the write path); wired into
      Settings' Logs tab on the frontend
- [x] Platform settings (super admin only) — `GET`/`PATCH` on
      `/api/v1/settings/fees`, `/settings/collection-account`,
      `/settings/integrations`, all three backed by the same singleton row
      (`platform_fee_settings`, `V6` added the new columns), plus full CRUD
      on `/api/v1/settings/subscription-plans` (`GET`/`POST`/`PATCH`/`DELETE`
      — the price list subscriptions checkout reads from, `V12`). The
      Paystack/Flutterwave/OPay keys entered here are no longer just stored
      for reference — subscription self-service checkout (see below) reads
      them live for real checkout and real server-side transaction
      verification
- [x] Co-operatives (super admin only) — `GET /api/v1/cooperatives` (list),
      `GET /api/v1/cooperatives/{id}`, `POST /api/v1/cooperatives` (onboard —
      creates the co-op and provisions its admin `Member` row, id =
      cooperative id, default password `admin123`, welcome email sent),
      `PATCH /api/v1/cooperatives/{id}` (edit — also syncs the admin's own
      name/email/phone), `PATCH /api/v1/cooperatives/{id}/status`
      (enable/disable — also locks/unlocks the admin's login); see
      `documentation/flows.md` for the full onboarding sequence
- [x] Subscriptions — the platform-wide gate: no co-op (never-subscribed or
      lapsed) can perform any mutating request anywhere, enforced once by
      `SubscriptionGateFilter`. Pricing comes from an editable Subscription
      Plans catalog (super admin, see above) — flexible durations, not a
      fixed Weekly/Monthly/Quarterly/Yearly formula. Super admin manual
      recording (`POST /api/v1/cooperatives/{id}/subscriptions`) and
      self-service Paystack/Flutterwave/OPay checkout for the co-op's own
      admin (`GET/POST /api/v1/subscriptions/me*`, real server-side
      verification against the gateway using keys from Settings ->
      Integrations, never a static env var); branded receipt emailed on
      every payment; see `documentation/flows.md`'s subscription lifecycle
      section
- [x] Dockerized (app + DB via `docker-compose.yml`), temporarily exposed
      publicly via a free ngrok static domain (stable URL, unlike a
      Cloudflare quick tunnel) while Azure access is pending — see
      `documentation/deployment.md` for the honest limits of that
- [x] Frontend wired to real login/me/logout, the dashboard summary,
      profile view/edit, the audit log, and platform settings (behind
      `NEXT_PUBLIC_USE_MOCK_*` flags on the frontend, so it can fall back to
      mock data if the tunnel is down)
- [ ] Azure SQL instance actually provisioned (blocked on Azure login —
      see the team for the connection details once it exists)
- [ ] Real Azure deployment (App Service or similar) — the Docker tunnel
      is a stopgap, not the destination
- [ ] Real domain endpoints (members/savings/loans *within* a co-op,
      notices) — one area at a time, per `documentation/api-contracts.md`
- [ ] Flutterwave checkout is implemented against Flutterwave's documented
      API but has not been exercised against a real Flutterwave sandbox
      account (no test keys were available) — verify with a real
      transaction before relying on it in production; Paystack has been
      tested end to end with a real test-mode payment
- [x] OPay checkout — confirmed working end to end against OPay's real
      **sandbox** API (`testapi.opaycheckout.com`): `initialize` returns a
      real hosted `checkoutUrl`, `confirm` correctly reaches `cashier/status`.
      Not live yet — the Nigeria-registered merchant account configured in
      Settings -> Integrations hasn't finished OPay's own live verification
      (dashboard shows "Test Mode, unverified"; live calls 403/fail with
      undocumented errors). Switching to live needs the correct per-country
      live host (see `documentation/flows.md`'s subscription lifecycle
      section — empirically different for NG vs EG merchants, undocumented
      by OPay) once that account is activated.

**Uploads are the one thing not yet cut over.** The frontend still uses its
own Cloudinary keys and its own `/api/upload` route for avatars. Once the
backend has a stable URL (real Azure deployment, not the tunnel), the
cutover is: point the frontend's upload call at
`POST {this API's URL}/api/v1/uploads`, remove `CLOUDINARY_*` from the
frontend's `.env.local` / Vercel project settings, delete
`src/app/api/upload/route.ts`. Do that in one step, not gradually, so
uploads are never broken in between.
