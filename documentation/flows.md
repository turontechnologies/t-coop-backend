# Request flows

Sequence diagrams for the flows the frontend now calls for real:
authentication (login/me/logout), the dashboard summary, profile
view/edit, and the audit log.

## Auth flow

```mermaid
sequenceDiagram
    participant U as Browser (Next.js)
    participant F as Frontend (Vercel)
    participant T as Tunnel (ngrok/Cloudflare)
    participant B as Spring Boot backend
    participant DB as Azure SQL / MSSQL

    U->>F: Submit login form (membershipId, password)
    F->>T: POST /api/v1/auth/login
    T->>B: forward request
    B->>DB: SELECT member WHERE id = :membershipId
    DB-->>B: member row (password_hash, role, status)
    B->>B: BCrypt.matches(password, password_hash)
    alt credentials valid and member Active
        B->>DB: INSERT audit_log (Authentication / Login / Success)
        B-->>T: 200 { token, member }
        T-->>F: 200 { token, member }
        F->>F: store token + member in Zustand (persisted)
        F-->>U: redirect to /dashboard
    else invalid
        B-->>T: 401 { error }
        T-->>F: 401 { error }
        F-->>U: show "Invalid membership ID or password"
    end

    Note over U,B: Every later request attaches Authorization: Bearer <token>
    U->>F: Load a protected page
    F->>T: GET /api/v1/auth/me (Bearer token)
    T->>B: forward request
    B->>B: JwtAuthenticationFilter validates token, sets principal = memberId
    B->>DB: SELECT member WHERE id = :memberId
    B-->>F: 200 { member }

    U->>F: Click "Logout"
    F->>T: POST /api/v1/auth/logout (Bearer token)
    T->>B: forward request
    B->>DB: INSERT audit_log (Authentication / Logout / Success)
    B-->>F: 200 { message: "Logged out" }
    F->>F: clear token + member from Zustand store
    F-->>U: redirect to /login
```

JWTs are stateless, so `/auth/logout` has nothing server-side to invalidate
today — it exists so logout is audit-logged and so the frontend always has
one call to make regardless of auth strategy. The token is discarded
client-side either way; if token revocation is ever needed, this endpoint is
where a blacklist check would be added.

**Any 401 from any endpoint (except `/auth/login` itself) forces the user
back to `/login`.** This is handled once, centrally, in the frontend's axios
response interceptor (`t-coop-app/src/lib/axios.ts`) — it clears the
Zustand auth store and hard-redirects, so an expired/invalid token on *any*
page (not just auth endpoints) recovers cleanly instead of showing a broken
page with failed requests. `/auth/login`'s own 401 (wrong password) is
excluded from this so a failed login attempt doesn't bounce the user in a
loop — that case is handled by the login form itself. This depends on 401
responses actually carrying CORS headers even when Spring Security
generates them directly (see api-conventions.md § CORS) — without that, the
browser reports a CORS failure instead of a 401 and this redirect never
fires.

## Dashboard summary flow

```mermaid
sequenceDiagram
    participant U as Browser (Next.js)
    participant F as Frontend (Vercel)
    participant B as Spring Boot backend
    participant DB as Azure SQL / MSSQL

    U->>F: Open /dashboard
    F->>B: GET /api/v1/dashboard/summary (Bearer token)
    B->>B: resolve caller (memberId) from JWT principal
    B->>DB: SELECT member WHERE id = :memberId

    alt role = super_admin
        B->>DB: COUNT(cooperatives), COUNT(members), SUM(savings), SUM(loans) — platform-wide
    else role = admin
        B->>DB: SUM(savings), SUM(loans) WHERE cooperative_id = caller's coop
    else role = member
        B->>DB: SUM(savings), SUM(loans) WHERE member_id = caller
    end

    B->>DB: latest 4 savings/loan records for the same scope
    B->>B: compute cards, illustrative hourly chart (real totals spread\nacross a fixed shape), and dividends (2% of savings)
    B-->>F: 200 { cards, chart, recentActivity }
    F->>F: map into SummaryCard/ActivityPoint/RecentActivity view models
    F-->>U: render QuickSummaryCards, ActivityChart, RecentActivities
```

Cards and `recentActivity` are real aggregates over `savings_records` /
`loan_records`. The chart and "dividends" have no dedicated ledger yet —
see `schema-design.md` "What's deliberately not modeled yet" — so they're
derived from the real totals rather than invented outright: dividends is
2% of savings (matching the frontend's pre-existing `SAVINGS_EARNINGS_RATE`
convention) and the hourly chart distributes each real total across the
same illustrative daily shape the old frontend mock used.

## Profile view/edit flow

```mermaid
sequenceDiagram
    participant U as Browser (Next.js)
    participant F as Frontend (Vercel)
    participant B as Spring Boot backend
    participant DB as Azure SQL / MSSQL

    U->>F: Open /profile (or /settings → Profile tab for super admin)
    F->>B: GET /api/v1/profile (Bearer token)
    B->>B: resolve caller (memberId) from JWT principal
    B->>DB: SELECT member WHERE id = :memberId
    B-->>F: 200 { membershipId, firstName, ..., guarantor }
    F-->>U: render form (loading skeleton while this is in flight)

    U->>F: Edit fields, click Save
    F->>F: zod-validate client-side first
    F->>B: PATCH /api/v1/profile (Bearer token, full record)
    alt valid
        B->>B: @Valid Jakarta Bean Validation (mirrors the frontend's zod schema)
        B->>DB: UPDATE members SET ... WHERE id = :memberId
        B->>DB: INSERT audit_log (Settings / Update / Profile / Success)
        B-->>F: 200 { updated record }
        F-->>U: toast "Profile updated successfully"
    else invalid
        B-->>F: 400 { error: "combined field messages" }
        F-->>U: toast with the real backend message (axios response\ninterceptor unwraps { error } into a normal Error)
    end
```

`/settings` → Profile tab (super admin only) edits a smaller subset of
fields than the full record (no NIN/bank account/gender/state/city) — the
frontend merges its edits onto the last-fetched full record before
sending the `PATCH`, so fields that tab doesn't show are never touched.
The `PATCH` request/validation on the backend is identical either way;
it doesn't know or care which UI sent it.

## Audit log flow

```mermaid
sequenceDiagram
    participant U as Browser (Next.js)
    participant F as Frontend (Vercel)
    participant B as Spring Boot backend
    participant DB as Azure SQL / MSSQL
    participant Geo as ipwho.is (free geo-IP)

    Note over B,DB: Every audited action (login, logout, profile update, ...)\nwrites one row synchronously — no external calls on this path,\nso the action itself is never slowed down by audit logging.
    B->>DB: INSERT audit_log (actor, module, action, resource, status, ip, created_at)

    U->>F: Open /settings → Logs tab (super admin only)
    F->>B: GET /api/v1/audit-log (Bearer token)
    B->>B: resolve caller, reject with 403 unless role = super_admin
    B->>DB: SELECT TOP 200 * FROM audit_log ORDER BY created_at DESC
    B->>DB: SELECT members WHERE id IN (distinct actor_ids) — batch, avoids N+1
    loop each distinct IP not already cached
        B->>Geo: GET ipwho.is/{ip}
        Geo-->>B: city, region, country (or failure — falls back to "Unknown")
        B->>B: cache result in memory for the life of the process
    end
    B-->>F: 200 [{ id, date, activityBy, role, module, action,\nresource, status, location, ipAddress }, ...]
    F-->>U: render table/cards, newest first
```

`module`/`action` values written anywhere in the backend **must** exactly
match the frontend's fixed `AuditModule`/`AuditAction` enums
(`t-coop-app/src/lib/audit-log-data.ts`) — this was a real bug once:
historical rows written before `ProfileController` used the right values
("Profile"/"Update Profile" instead of "Settings"/"Update") had no icon
mapping on the frontend and crashed the entire Logs tab. Fixed both ways —
the write side now uses the correct values, a migration corrected the
existing bad rows, and the frontend now falls back to a neutral icon for
any value it doesn't recognize instead of crashing, so a future mismatch
degrades gracefully instead of taking the page down.
