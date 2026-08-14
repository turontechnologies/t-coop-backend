# API conventions

Ground rules for every endpoint built in this backend, so things stay
consistent as more than one person adds routes.

## Base path

- **`/api/v1/...`** — every real business endpoint (auth, cooperatives,
  members, savings, loans, notices, subscriptions, uploads, ...). The `v1`
  is there so a breaking change later can live at `/api/v2/...` alongside
  it instead of breaking every existing client.
- **`/api/health`** — liveness check, deliberately outside `/v1` since it's
  an ops concern, not a versioned business contract.

`api-contracts.md` (the frontend's spec) writes paths without the `/api/v1`
prefix for brevity (e.g. `POST /cooperatives`) — read those as
`POST /api/v1/cooperatives`.

## Auth

Every endpoint except `POST /api/v1/auth/*` requires:

```
Authorization: Bearer <jwt>
```

The token encodes the member's `id` and `role`. Role/co-op scoping (e.g.
"an `admin` only ever sees their own co-op's data") is enforced
server-side, in the controller/service layer — never trust a client-
supplied `cooperativeId` query param as the only check.

## Error responses

```json
{ "error": "Human-readable message" }
```

Same shape whether it's a validation error (400), an auth failure (401/403),
a not-found (404), or an upstream failure like a failed Paystack transfer
(502). Status code carries the category; the frontend just displays
`error`.

## Money & dates

- Money: plain numbers, no currency symbol, in the co-op's own currency
  (see the frontend's per-co-op currency feature) — never kobo/cents.
- Dates: ISO 8601 (`YYYY-MM-DD` for date-only, full ISO datetime with `Z`
  for timestamps).

## CORS

Configured centrally in `config/CorsConfig.java`, driven by the
`FRONTEND_ORIGINS` env var — never add `@CrossOrigin` on individual
controllers, it'll drift out of sync with the real allow-list.
