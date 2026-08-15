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

**This is enforced automatically, for every controller** — `GlobalExceptionHandler`
(`common/GlobalExceptionHandler.java`) catches `@Valid` bean-validation
failures (`MethodArgumentNotValidException`, combines every field error
into one message), malformed/missing request bodies
(`HttpMessageNotReadableException`), and anything else unhandled — so no
controller needs its own try/catch to keep the error shape consistent.
Add `@Valid` + Jakarta Bean Validation annotations on a request record
(see `ProfileUpdateRequest` for the pattern) and this is handled for free;
don't write a bespoke exception handler per controller.

## Money & dates

- Money: plain numbers, no currency symbol, in the co-op's own currency
  (see the frontend's per-co-op currency feature) — never kobo/cents.
- Dates: ISO 8601 (`YYYY-MM-DD` for date-only, full ISO datetime with `Z`
  for timestamps).

## CORS

Configured centrally in `config/CorsConfig.java` (a `CorsConfigurationSource`
bean, driven by the `FRONTEND_ORIGINS` env var) and wired into
`SecurityConfig` via `http.cors(cors -> cors.configurationSource(...))` —
never add `@CrossOrigin` on individual controllers, it'll drift out of sync
with the real allow-list.

**Every new endpoint works automatically — nothing to remember per-route.**
Two things had to both be true for this, and both were real bugs once
before they were fixed:

1. `SecurityConfig` permits all `OPTIONS` requests platform-wide
   (`requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()`) — preflight
   requests never carry the `Authorization` header, so without this,
   Security would reject the preflight itself with 401 before any CORS
   headers get added.
2. CORS is registered **inside the security filter chain** (`http.cors(...)`),
   not only as a `WebMvcConfigurer`. A `WebMvcConfigurer`'s CORS handling
   only runs once a request reaches Spring MVC's `DispatcherServlet` — but a
   401 generated directly by Security (e.g. `JsonAuthenticationEntryPoint`
   rejecting a bad/expired token) never reaches MVC at all. A
   `WebMvcConfigurer`-only setup would add `Access-Control-Allow-Origin` to
   every *successful* response and to preflights, but silently omit it from
   auth-failure responses — which the browser then reports as a generic CORS
   error instead of surfacing the real 401 to application code (this is
   exactly what broke the frontend's "redirect to login on 401" handling
   until this was found).

If CORS errors show up in the browser console for a route that works fine
via curl/Thunder Client, suspect exactly this: curl doesn't enforce or even
look at CORS headers, so it will never reproduce this class of bug — only a
real browser will.
