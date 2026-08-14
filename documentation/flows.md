# Request flows

Sequence diagrams for the two flows the frontend now calls for real:
authentication (login/me/logout) and the dashboard summary.

## Auth flow

```mermaid
sequenceDiagram
    participant U as Browser (Next.js)
    participant F as Frontend (Vercel)
    participant T as Cloudflare Tunnel
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
