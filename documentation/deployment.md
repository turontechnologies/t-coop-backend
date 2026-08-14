# Deployment

Two very different things live in this doc — don't confuse them.

## 1. Local Docker stack (`docker-compose.yml`)

Runs the whole backend — the Spring Boot app **and** its own SQL Server —
in containers on one machine, wired together automatically. This is for
development, not for anyone else to depend on long-term.

```bash
cp .env.example .env   # fill in JWT_SECRET at minimum (openssl rand -base64 32)
docker compose up -d --build
```

What happens:
- `db` — SQL Server 2022, data persisted in a named Docker volume
  (survives `docker compose down`, wiped only by `docker compose down -v`)
- `db-init` — a one-shot service that creates the `tcoopdb` database if it
  doesn't exist yet, then exits
- `app` — the Spring Boot app, waits for `db-init` to finish, then boots
  and Flyway applies every migration automatically

Confirm it's up: `curl http://localhost:8080/api/health`

Seeded demo accounts (see `V2__seed_demo_users.sql`) — same IDs as the
frontend's mock users, password `admin123` for all three:

| ID | Role |
|---|---|
| `SA-0001` | super_admin |
| `AD-0001` | admin |
| `MB-0001` | member |

Tear down: `docker compose down` (add `-v` to also wipe the database).

## 2. Public tunnel (temporary — not a real deployment)

While waiting for the real Azure deployment, the local Docker stack above
can be exposed to the internet with a Cloudflare quick tunnel — no account,
no signup:

```bash
cloudflared tunnel --url http://localhost:8080
```

This prints a public `https://<random-words>.trycloudflare.com` URL that
forwards straight to the local container. Point the frontend's
`NEXT_PUBLIC_API_URL` (or equivalent) at that URL to test the real backend
against the live Vercel site.

**Be honest with the team about what this is and isn't:**
- The URL **changes every time the tunnel restarts** — it's not stable.
  Update the frontend's env var each time, or coordinate a restart window.
- It only works while **this specific machine, this specific Docker stack,
  and this specific `cloudflared` process are all running**. Close the
  laptop, lose the connection. There's no uptime guarantee — Cloudflare
  says so explicitly for these "quick tunnels."
- It's for **proving connectivity and testing**, not for anything anyone
  should rely on being up. Don't put this URL anywhere permanent (like a
  committed `.env.example` default).

## 3. Real deployment (Azure — once provisioned)

Once the Azure SQL Database exists (see the team for status) and an Azure
App Service is stood up for the backend:

- `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` point at the real Azure SQL instance
  instead of the local Docker one
- The same `Dockerfile` can deploy straight to Azure App Service's
  container support (`az webapp create --deployment-container-image-name`),
  or Azure can build it directly from source — decide when we get there
- `FRONTEND_ORIGINS` is updated to the real Vercel production URL
- CI/CD (GitHub Actions) builds and deploys on push to `main`, instead of
  this manual `docker compose` flow

This is the actual target — the local stack and the tunnel are both
scaffolding to keep moving while Azure access is pending, not a
replacement for it.
