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
can be exposed to the internet with a tunnel. Two options, pick based on
whether you need the URL to stay the same across restarts:

### 2a. ngrok with a free static domain (stable URL — current default)

Free ngrok account (no card required) gives one permanent static domain
per account (`https://<your-name>.ngrok-free.dev`) — same URL every time
you restart the tunnel, unlike a Cloudflare quick tunnel.

One-time setup (per person, via the ngrok dashboard — not scriptable):
1. Sign up free at [dashboard.ngrok.com/signup](https://dashboard.ngrok.com/signup)
2. Get your authtoken from [dashboard.ngrok.com/get-started/your-authtoken](https://dashboard.ngrok.com/get-started/your-authtoken),
   then `ngrok config add-authtoken <token>`
3. Claim your free static domain at [dashboard.ngrok.com/domains](https://dashboard.ngrok.com/domains)
   (one per account, name is randomly assigned — custom names need a paid
   plan)

Then, every time you want the backend reachable:

```bash
ngrok http --url=<your-domain>.ngrok-free.dev 8080
```

The current project's domain in use is `hamster-probiotic-compile.ngrok-free.dev`
— whoever is running the backend locally needs their own ngrok account and
authtoken (the static domain is tied to one account), and the frontend's
`NEXT_PUBLIC_API_URL` needs updating if a different person's domain is
used.

### 2b. Cloudflare quick tunnel (no account, but URL changes every restart)

```bash
cloudflared tunnel --url http://localhost:8080
```

Prints a fresh `https://<random-words>.trycloudflare.com` URL every time
— fine for a one-off test, painful for anything longer since the frontend's
env var needs updating each restart.

**Be honest with the team about what either of these is and isn't:**
- Both only work while **this specific machine, this specific Docker
  stack, and this specific tunnel process are all running**. Close the
  laptop, lose the connection. There's no uptime guarantee — neither
  ngrok's free tier nor Cloudflare's quick tunnels are meant for anything
  production-facing.
- They're for **proving connectivity and testing**, not for anything
  anyone should rely on being up. Real uptime is still the Azure
  deployment in step 3 below.

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
