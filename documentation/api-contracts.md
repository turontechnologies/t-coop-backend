# T-Coop API Contracts

For the backend engineer. This app currently runs on frontend mock data — everything
below is what the real API needs to provide instead. All endpoints except **Auth**
require `Authorization: Bearer <token>`. Money amounts are in Naira (numbers, not kobo)
unless noted. Dates are ISO 8601 strings.

Three roles: `super_admin` (oversees all co-ops), `admin` (manages one co-op), `member`
(belongs to one co-op).

---

## 1. Auth

### `POST /auth/login`

```json
// Request
{ "membershipId": "COOP-0001", "password": "string", "keepLoggedIn": true }

// Response
{
  "token": "string",
  "member": { "id": "COOP-0001", "name": "string", "email": "string", "role": "admin", "avatarUrl": "string?" }
}
```

Login is by **membership ID**, not email. A co-op's admin logs in with the **co-op's own ID**
(there's no separate `AD-XXXX` admin ID — see §2). A member who is not `Active` gets a
distinct `403 { "error": "Your account is not active. Please contact Turon Technologies for
assistance." }` instead of the generic invalid-credentials message — but only once the
password has already matched, so a wrong-password guess against a disabled account still
gets the generic message and can't be used to fish for which accounts are disabled.

### `POST /auth/forgot-password`

```json
// Request
{ "email": "string" }
// Response
{ "message": "OTP sent" }
```

Sends the OTP by email/SMS server-side. **Do not return the OTP in the response** (the current mock does this — it's a known gap to close).

### `POST /auth/verify-otp`

```json
// Request
{ "email": "string", "otp": "123456" }
// Response
{ "resetToken": "string" }
```

### `POST /auth/reset-password`

```json
// Request
{ "resetToken": "string", "newPassword": "string" }
// Response
{ "message": "Password updated" }
```

### `POST /auth/change-password` (authenticated)

```json
// Request
{ "currentPassword": "string", "newPassword": "string" }
// Response
{ "message": "Password updated" }
```

### `GET /auth/me`

```json
// Response
{
  "id": "string",
  "name": "string",
  "email": "string",
  "role": "super_admin|admin|member",
  "avatarUrl": "string?"
}
```

### `POST /auth/logout`

```json
// Response
{ "message": "Logged out" }
```

---

## 2. Cooperatives (super_admin only)

**A co-op IS its admin account.** Onboarding a co-op creates exactly one `Member` row
(`role: "admin"`) whose `id` is the co-op's own `id` — not a separately generated one. That
admin logs in with the co-op ID and the platform default password (`admin123`), and is
expected to change it from Settings. This keeps "how many co-ops has super admin onboarded"
and "how many admins exist" the same number by construction, and means the co-op's members
(`Member` rows with `cooperativeId` = that co-op's id) are exactly that admin's members.

### `GET /cooperatives`

Returns list with computed totals (backend computes `memberCount`, `savingsTypeCount`,
`loanTypeCount`, `totalSavings`, `totalLoans` — don't make the frontend sum/count records itself).

```json
// Response
[
  {
    "id": "COOP-0001",
    "name": "string",
    "adminName": "string",
    "contactEmail": "string",
    "contactPhone": "string",
    "address": "string",
    "country": "string",
    "state": "string",
    "city": "string",
    "status": "Active|Disabled",
    "currency": "NGN",
    "memberCount": 12,
    "savingsTypeCount": 3,
    "loanTypeCount": 3,
    "totalSavings": 960000,
    "totalLoans": 560000
  }
]
```

`savingsTypeCount` (`SavingsTypeRepository.countByCooperativeId`) powers the super admin's
platform-wide `/savings` oversight table's "No of Savings Types" column — not invented, not a
hardcoded 3; a freshly onboarded co-op with none configured yet genuinely returns `0`.
`loanTypeCount` (`LoanTypeRepository.countByCooperativeId`) does the same for `/loans` — it's
`3` for every real co-op today only because `V17` explicitly backfilled that value, not because
anything auto-seeds it going forward.

### `POST /cooperatives`

```json
// Request
{
  "coopId": "string",
  "coopName": "string",
  "adminFirstName": "string",
  "adminLastName": "string",
  "contactEmail": "string",
  "contactPhone": "string",
  "address": "string",
  "country": "string",
  "state": "string",
  "city": "string"
}
// Response: the created Cooperative (same shape as GET /cooperatives item)
```

### `GET /cooperatives/:id`

Same shape as one list item. The savings type breakdown (`savingsByType` below this section once
implied it'd nest here) is its own dedicated endpoint instead — see §5 — matching the
Subscriptions super-admin pattern (§8) rather than growing this DTO. `loansByType` is still
unbuilt.

### `GET /cooperatives/:id/members` — **built**

Every real member (`admin`/`member` role) of one co-op — the super-admin oversight Members tab.
Read-only: no `PATCH` exists yet to edit a member's profile or toggle their status from this
view (that's still the frontend's mock `useCoopStore`) — the frontend must not offer those
actions as if they persist against this data.

```json
[
  {
    "id": "string",
    "firstName": "string",
    "lastName": "string",
    "email": "string",
    "role": "admin|member",
    "status": "Active|Inactive",
    "guarantor": "string?",
    "country": "string?",
    "state": "string?",
    "city": "string?",
    "bankCode": "string?",
    "accountNumber": "string?",
    "accountName": "string?"
  }
]
```

### `PATCH /cooperatives/:id`

```json
// Request — editable fields only; id/status/currency/subscriptionFee are untouched
{
  "name": "string",
  "adminFirstName": "string",
  "adminLastName": "string",
  "contactEmail": "string",
  "contactPhone": "string",
  "address": "string",
  "country": "string",
  "state": "string",
  "city": "string"
}
// Response: the updated Cooperative (same shape as GET /cooperatives item)
```

Since the co-op's admin identity lives on the same `Member` row the admin logs in and edits
their own profile as (see above), this endpoint also writes `adminFirstName`/`adminLastName`/
`contactEmail`/`contactPhone` through to that row — a super admin's edit here is what the
admin sees reflected in their own portal, immediately, with no separate sync step.

### `PATCH /cooperatives/:id/status`

```json
{ "status": "Active|Disabled" }
```

Disabling a co-op also flips its admin `Member` row's status (mapped to `Inactive`, since
`Member.status` only allows `Active|Inactive` while `Cooperative.status` allows
`Active|Disabled`) — so a disabled co-op's admin is locked out of login immediately, with
the friendly "account not active" message from §1.

---

## 3. Members (scoped to a cooperative)

```ts
// CoopMember shape, returned by all endpoints below (backend's `role`/`status` are actually
// lowercase "admin"|"member"|"super_admin" — the frontend's CoopMemberDto mapper capitalizes
// role for display; status is already "Active"|"Inactive" on both sides)
{
  "id": "string", "firstName": "string", "lastName": "string", "email": "string",
  "role": "admin|member", "status": "Active|Inactive", "guarantor": "string",
  "country": "string", "state": "string", "city": "string",
  "bankCode": "string", "accountNumber": "string", "accountName": "string"
}
```

- `GET /cooperatives/:id/members` — **built**. `super_admin` any co-op; `admin` only their own (checked server-side against their own `cooperativeId`, never the path param).
- `POST /cooperatives/:id/members` — **built**. Request = above shape (as `MemberCreateRequest`) minus `id`/`status`, plus `membershipId` (caller-chosen, like a co-op's own id), plus `otherName`/`gender`/`phone`/`homeAddress`/`facebook`/`twitter` (not in the read DTO but accepted on create, mirroring co-op admin onboarding). Same conventions as onboarding a co-op: password starts as the platform default (`admin123`), a welcome email goes out with the membership ID and password. 409 if the membership ID or email is already taken.
- `POST /cooperatives/:id/members/bulk` — not yet built.
- `GET /cooperatives/:id/members/:memberId` — not yet built as its own endpoint (the frontend finds the member client-side out of the list response instead).
- `PATCH /cooperatives/:id/members/:memberId` — **built**. Updates `firstName, lastName, email, role, guarantor, country, state, city, bankCode, accountNumber, accountName`; fields not in this request (`otherName`, `gender`, `phone`, `nin`, `facebook`, `twitter`) are preserved as-is, never blanked.
- `PATCH /cooperatives/:id/members/:memberId/status` — **built**. `{ "status": "Active|Inactive" }`.

**Bank fields**: `accountName` is never typed by the user — it's filled in from `POST /banks/resolve` (§10) after the user picks a bank + types an account number.

---

## 4. Profile (self-service, any authenticated member)

### `GET /profile`

```json
{
  "membershipId": "string",
  "accountNumber": "string",
  "bankCode": "string",
  "accountName": "string",
  "nin": "string",
  "firstName": "string",
  "lastName": "string",
  "otherName": "string?",
  "gender": "Male|Female|Other",
  "phone": "string",
  "email": "string",
  "homeAddress": "string",
  "country": "string",
  "state": "string",
  "city": "string",
  "facebook": "string?",
  "twitter": "string?",
  "guarantor": "string?"
}
```

### `PATCH /profile`

Same shape as request body (minus `membershipId`, which is fixed). Returns the updated record.

**Built.** `ProfileController`/`ProfileDto`/`ProfileUpdateRequest` — server-side validation mirrors the frontend's zod schema exactly (10-digit account number, 11-digit NIN, valid email, etc.), so a request that passes client-side validation never fails here. GET is a single `findById` (sub-100ms locally). Every update is audit-logged (module `Settings` / action `Update` / resource `Profile` — matching the frontend's fixed audit-log enums). Validation failures return `{"error": "combined human-readable message"}` via the new `GlobalExceptionHandler` — see api-conventions.md.

### `POST /profile/password`

```json
// Request
{ "currentPassword": "string", "newPassword": "string" }
// Response
{ "message": "Password updated" }
```

**Built.** Verifies `currentPassword` against the stored bcrypt hash (400 `"Current password is incorrect"` if it doesn't match — never reveals whether the *membership ID* is valid, same principle as login), requires `newPassword` to be at least 6 characters (matches the frontend's `settingsProfileSchema`), re-hashes and saves. Audit-logged as module `Settings` / action `Update` / resource `Password`. Self-service only — there's no separate "admin resets someone else's password" endpoint yet.

---

## 5. Savings

**Super-admin oversight is built** (`SavingsController`) — read-only, backing the
`/co-operatives/[id]/savings/...` pages. The admin/member self-service endpoints below it
("Upload Teller", member deposit/withdrawal requests, request approval) are **not yet built** —
that flow still lives entirely in the frontend's mock `useCoopStore`, same as before; see
`t-coop-app/documentation/savings-page.md`. Building those against this backend is a separate,
larger task (it needs a `SavingsRequest` entity/table wiring, and — on `Approved` + `Withdrawal`
— the same real-payout guarantee already built for Subscriptions/Loans, per §10).

### `GET /cooperatives/:id/savings/types` — **built**

The "Members Savings" breakdown table. One row per savings product the co-op actually offers —
no auto-seed, no invented defaults; a newly onboarded co-op legitimately returns `[]` here until
someone deliberately configures a type (no management UI yet — the row shape supports it, this
is purely a future task).

```json
[
  {
    "id": "uuid",
    "name": "Basic Savings",
    "min": 5000,
    "max": 10000,
    "status": "Active|Inactive",
    "earnings": 4700,
    "total": 235000
  }
]
```

`earnings` is a flat 2% of `total` — illustrative, not a real interest-accrual engine (matches
the frontend's pre-existing `SAVINGS_EARNINGS_RATE`, same honesty note as the dashboard's
dividends figure). `total`/`earnings` only ever sum `Success`-status records.

### `GET /cooperatives/:id/savings` — **built**

List savings records for one co-op, newest first. Every filter is optional:
`?memberId=&type=&status=&from=&to=` (`type` matches a savings type's **name**, not id).

```json
[
  {
    "id": "uuid",
    "memberId": "string",
    "memberName": "string",
    "savingsType": "string",
    "amount": 90000,
    "balanceAfter": 90000,
    "method": "Paystack|Manual Upload",
    "transactionId": "string",
    "date": "2026-07-09",
    "status": "Success|Pending|Failed",
    "receiptUrl": "string?"
  }
]
```

### `GET /savings/:recordId` — **built**

Single record, same shape as one list item above.

### `POST /cooperatives/:id/savings` (admin manual/teller upload) — not yet built

```json
// Request
{
  "memberId": "string",
  "savingsType": "string",
  "amount": 90000,
  "receiptFile": "multipart file?"
}
// Response: the created record (balanceAfter computed server-side)
```

### `POST /savings/requests` (member self-service) — not yet built

```json
// Request
{ "savingsType": "string", "type": "Deposit|Withdrawal", "amount": 90000, "note": "string?" }
// Response
{
  "id": "string", "memberId": "string", "memberName": "string",
  "type": "Deposit|Withdrawal", "savingsType": "string", "amount": 90000,
  "note": "string?", "status": "Pending", "requestedAt": "iso-datetime"
}
```

### `GET /cooperatives/:id/savings/requests` — not yet built

List, same shape as above plus `resolvedAt`. `admin` sees their own co-op's; `super_admin` can pass no `:id` filter to see all.

### `PATCH /savings/requests/:id` — not yet built

```json
// Request
{ "status": "Approved|Declined" }
```

On `Approved` + `type: Withdrawal`: backend must trigger a real payout via §10 (`/payouts/transfer`) to the member's saved bank details before marking it Approved. If the transfer fails, the request must stay `Pending` and the error surfaced back to the client — don't mark Approved if money didn't move.

---

## 6. Loans

**Super-admin oversight is built** (`LoanController`) — read-only, mirroring §5 Savings exactly.
Everything below that isn't marked **built** is still the frontend's mock `useCoopStore` — see
`t-coop-app/documentation/loans-page.md`.

### `GET /cooperatives/:id/loans/types` — **built**

The "Loans" breakdown table. Unlike savings types, loan types for real co-ops *are* seeded —
`V17__seed_loan_types.sql` gave every co-op the same three products (Emergency/Education/
Business Loan) as a deliberate one-time backfill, not an auto-seed-on-create hook (no new co-op
gets these automatically going forward — see the migration's own note for why that split exists).

```json
[
  {
    "id": "uuid",
    "name": "Emergency Loan",
    "eligibilityPercent": 300,
    "durationMonths": 3,
    "numberOfRepayments": 3,
    "interestRate": 5,
    "status": "Active|Inactive",
    "earnings": 3500
  }
]
```

`earnings` ("Earnings on Loan") = `sum(totalRepayment - amount)` over every non-`Rejected` loan
of that type — real interest collected, not illustrative like Savings' 2% figure, matching the
frontend's `coopLoansBySummaryType` math exactly.

### `GET /cooperatives/:id/loans` — **built**

List, newest first. Every filter optional: `?memberId=&type=&status=&from=&to=` (`type` matches
a loan type's name, not id).

```json
[
  {
    "id": "uuid", "memberId": "string", "memberName": "string", "loanType": "string",
    "amount": 40000, "interestRate": 5, "durationMonths": 3, "numberOfRepayments": 3,
    "monthlyRepayment": 14000, "totalRepayment": 42000, "guarantorName": "string",
    "date": "iso-date", "status": "Awaiting Guarantor|Awaiting Admin|Active|Completed|Rejected",
    "repaymentsMade": 3, "guarantorDocumentUrl": "string?", "rejectionReason": "string?"
  }
]
```

### `GET /loans/:recordId` — **built**

Single record, same shape as one list item above.

### `GET /loans/eligibility?memberId=&loanType=` — not yet built

```json
{ "eligibleAmount": 150000 }
```

Computed as `min(maxAmount, totalSavings * eligibilityPercent / 100)`.

### `POST /cooperatives/:id/loans` (member requests a loan) — not yet built

```json
// Request
{ "loanType": "string", "amount": 50000, "guarantorId": "string" }
// Response — status starts "Awaiting Guarantor"
```
Response shape matches the list item above.

### `PATCH /loans/:id/guarantor-response` — not yet built

```json
// Request
{ "decision": "Accepted|Rejected", "documentFile": "multipart file?" }
```

`Accepted` → status moves to `Awaiting Admin`. `Rejected` → status moves to `Rejected`.

### `PATCH /loans/:id/decision` (admin) — not yet built

```json
// Request
{ "decision": "Approved|Rejected", "rejectionReason": "string?" }
```

On `Approved`: trigger a real payout via §10 to the member's bank details, same "don't mark Approved unless the transfer succeeded" rule as savings withdrawals. Moves status to `Active` (or `Rejected`).

### `GET /loans/:id/repayment-schedule` — not yet built

```json
[
  {
    "installment": 1,
    "amount": 16666,
    "interest": 833,
    "totalAmount": 17500,
    "dueDate": "iso-date",
    "status": "Paid|Upcoming|Overdue|Pending"
  }
]
```

**Note**: the current frontend only _displays_ a computed schedule (derived client-side from the
now-real `amount`/`numberOfRepayments`/`totalRepayment`/`date`/`status`/`repaymentsMade` fields
via `generateRepaymentSchedule` in `loans-data.ts`) and never records an actual repayment —
there's no "make a repayment" action anywhere in the UI today. Flagging this as a real gap:
decide with product whether repayments are auto-deducted (wallet), manually recorded by admin,
or out of scope for this phase, then add a `POST /loans/:id/repayments` endpoint accordingly.

---

## 7. Notices — **built** (2026-08-24)

```ts
// Notice shape (GET/POST response)
{
  "id": "uuid", "type": "General|Meeting Notice|Meeting Minutes",
  "title": "string", "message": "string",
  "recipient": "All Members|All Admins|All Members & Admins",
  "medium": "Email|SMS|Email & SMS",
  "meetingDate": "iso-date|null",       // only for "Meeting Notice"
  "attachment": { "name": "string", "url": "string", "size": 12345 } | null,
  "sendAt": "iso-instant",              // future = scheduled, not yet sent
  "status": "Scheduled|Sent",           // derived server-side, not stored
  "createdByName": "string", "createdByRole": "super_admin|admin|member",
  "createdAt": "iso-instant",
  "targetCoopIds": ["string"]           // always non-empty; see tenant-isolation note below
}
```

**Tenant isolation is enforced server-side, not just by what the client sends.** An admin caller's
`targetCoopIds` in the create request is ignored/overridden to `[their own cooperativeId]` — only
a `super_admin` caller can actually address more than one co-op, and even then every id must exist.
The same `isVisible(notice, caller)` check gates every read/reply/resend/delete: `super_admin` sees
everything; `admin`/`member` only ever see notices targeting their own co-op, and a `member` never
sees a still-`Scheduled` one or one whose `recipient` excludes them.

- `GET /notices` — list (capped at 300, newest `sendAt` first), scoped per caller as above.
- `POST /notices` — create. `sendAt` is the final computed ISO instant (frontend resolves "now" vs
  scheduled date+time before sending). `attachment`, if present, is `{name, url, size}` — get the
  `url` from `POST /uploads/attachment` first (see section 11). A notice created for "now" notifies
  its audience immediately via the Notifications system (section 14) and, per its `medium`, sends
  real email (`EmailService.sendNoticeEmail`, always available) and/or real SMS
  (`SmsService`/Termii, needs Settings → Integrations configured — see section 13); a scheduled
  one only notifies/emails/texts once resent — no minute-granularity dispatcher exists for the
  exact scheduled moment.
- `GET /notices/:id` — detail; 404 (not 403) if the caller can't see it, to avoid leaking existence.
- `DELETE /notices/:id` — also deletes its replies and target rows (DB cascade).
- `POST /notices/:id/resend` — sets `sendAt` to now, re-fires the notification fan-out.
- `GET /notices/:id/replies` — `[{ "id", "noticeId", "authorId", "authorName", "authorRole", "authorAvatarUrl", "message", "createdAt" }]` — author fields resolved live from the current `Member` row, not a snapshot.
- `POST /notices/:id/replies` — `{ "message": "string" }`.

No per-notice read-tracking endpoint — that concern moved entirely to the generic Notifications
system (section 14); a notice itself doesn't carry read state anymore, only the notification that
pointed to it does.

---

## 8. Subscriptions

**No co-op can act on the platform (any non-GET request, anywhere) without an active
subscription** — a co-op that has never paid is treated identically to one that lapsed. This is
enforced once, centrally, by `SubscriptionGateFilter`, not scattered across individual
controllers. `Cooperative.subscriptionCycle`/`subscriptionExpiresAt` decide this; both start
null until the first payment.

Pricing itself is a super-admin-managed catalog (**Subscription Plans**, §8a) — every duration
(Weekly/Monthly/custom) and price is something the super admin added, not a fixed formula, and
New Subscription / Renewal are priced independently.

```ts
// SubscriptionPayment shape
{
  "id": "string", "paymentRef": "string", "amountPaid": 300000,
  "method": "Manual|Paystack|Flutterwave", "date": "iso-date",
  "type": "New Subscription|Renewal", // auto-detected server-side from payment history — never client-supplied
  "cycle": "string", // snapshot of the plan's label at the time — free text, not an enum
  "status": "Active|Overdue",
  "resultingExpiresAt": "iso-date" // what THIS payment extended the subscription to — captured at the time, so a receipt re-downloaded later is still accurate
}
```

### 8a. Subscription Plans (`super_admin` only) — Payment Settings → Subscription Plans

The editable price list everything else in this section reads from. Freely
addable/editable/deletable — `durationInDays` is the flexible unit (not a fixed enum), so a plan
can be any length ("Weekly", "6 Months", "18 Months", whatever). Deleting a plan never corrupts
payment history — `SubscriptionPayment.cycle` is a label snapshot, not a foreign key.

```ts
// SubscriptionPlan shape
{
  "id": "uuid", "type": "New Subscription|Renewal", "label": "string",
  "durationInDays": 30, "amount": 12500, "status": "Active|Inactive"
}
```

- `GET /settings/subscription-plans` — list all.
- `POST /settings/subscription-plans` — `{ type, label, durationInDays, amount }` → creates
  (`status` always starts `"Active"`).
- `PATCH /settings/subscription-plans/:id` — `{ label, durationInDays, amount, status }`. `type`
  is never editable — delete and re-add to move a plan between New Subscription and Renewal.
- `DELETE /settings/subscription-plans/:id` — hard delete.

An `Inactive` plan stays visible in past payment history but can no longer be picked for a new
payment (filtered out of both `GET /subscriptions/me` and the super admin's manual-recording
picker on the frontend).

### Super admin — manual recording (`super_admin` only)

### `GET /subscriptions`

List every co-op's subscription standing.

```json
[
  {
    "coopId": "string", "coopName": "string",
    "revenueEarned": 900000, "subscriptionFee": 300000,
    "subscriptionCycle": "string|null",
    "lastPaymentDate": "iso-date|null", "subscriptionExpiresAt": "iso-date|null",
    "status": "Active|Overdue"
  }
]
```

`subscriptionFee` here is `Cooperative.subscriptionFee` — the co-op's own onboarding-time
figure, kept as an informational field only. It's no longer what self-service/manual pricing is
computed from; that's entirely the Subscription Plans catalog now.

### `GET /subscriptions/summary`

```json
{ "mgtFeesReceived": 1200000 }
```

### `GET /cooperatives/:id/subscriptions`

Full payment history for one co-op, newest first — array of `SubscriptionPayment`.

### `POST /cooperatives/:id/subscriptions`

A manual record of money already received (bank transfer, cheque, etc.) — not a gateway call.
`planId` picks the label/duration from the Subscription Plans catalog; `amountPaid` stays
free-typed since a real external payment can legitimately differ from the catalog's listed
price (partial, discounted, negotiated).

```json
// Request
{ "amountPaid": 75000, "planId": "uuid" }
// Response: { "payment": SubscriptionPayment, "nextRenewalDate": "iso-date" }
```

### Self-service (`admin`, the caller's own co-op only)

The one path exempted from the platform-wide subscription lock — how a dormant admin pays their
way back in, via Paystack or Flutterwave (whichever the super admin enabled and entered real
keys for in Settings → Integrations). Amounts are always computed/verified server-side —
`initialize` decides the price from the plan catalog, `confirm` re-verifies against the real
gateway API before crediting anything.

#### `GET /subscriptions/me`

```json
{
  "coopId": "string", "coopName": "string", "adminName": "string",
  "status": "Active|Overdue",
  "subscriptionCycle": "string|null",
  "subscriptionExpiresAt": "iso-date|null",
  "availablePlans": [SubscriptionPlan, ...], // Active plans of the type matching this co-op's current state
  "availableGateways": [{ "gateway": "Paystack|Flutterwave", "publicKey": "string" }]
}
```

`availablePlans` is auto-scoped: `New Subscription` plans if `subscriptionExpiresAt` is still
null (never paid), `Renewal` plans otherwise — matching exactly what a payment against this
co-op would be classified as.

#### `GET /subscriptions/me/history`

Same shape as `GET /cooperatives/:id/subscriptions`, scoped to the caller's own co-op.

#### `POST /subscriptions/me/initialize`

```json
// Request
{ "planId": "uuid", "gateway": "Paystack|Flutterwave" }
// Response
{ "reference": "string", "amount": 25000, "gateway": "Paystack", "publicKey": "string" }
```

`amount` comes straight from the plan — never client-supplied. `409` if the plan's `type`
doesn't match what this co-op can currently buy (e.g. picking a Renewal plan before ever
subscribing). `reference`/`amount`/`cycle`/`durationInDays`/`gateway` are persisted as a
`Pending` intent that `confirm` looks up by reference.

#### `POST /subscriptions/me/confirm`

```json
// Request
{ "reference": "string" }
// Response: SubscriptionReceipt — { coopId, coopName, adminName, paymentRef, amountPaid, method,
//   date, type, cycle, status, nextRenewalDate }
```

Verifies the reference against the real gateway's transaction-verify endpoint server-side
(`PaymentGatewayService`) using the secret key from `PlatformSettings`. `402` if verification
fails or the gateway-reported amount is less than the intent's amount; `409` if that reference
was already confirmed once.

---

## 9. Dashboard summary

### `GET /dashboard/summary`

Role-aware; scope to the caller's co-op for `admin`/`member`, platform-wide for `super_admin`.

```json
{
  "cards": [{ "label": "Total Savings", "value": 209000000 }, ...],
  "chart": [{ "hour": "9am", "savings": 12000, "loans": 4000, "dividends": 800 }, ...],
  "recentActivity": [{ "title": "string", "subtitle": "string", "amount": 90000, "date": "iso-datetime", "status": "string?" }]
}
```

**Built.** Cards and `recentActivity` are real aggregation queries over `savings_records`/`loan_records` (`DashboardController`/`DashboardService`). The `chart` and any `Dividends` figure have no dedicated table behind them, so they're derived from the real totals rather than invented outright — see `documentation/flows.md` for exactly what's real vs. illustrative.

---

## 10. Bank & Payouts (already built — Paystack-backed)

These four already work today as real Next.js API routes proxying Paystack. Decide with the backend engineer whether Next.js keeps owning them or they move server-side — the contract stays the same either way.

### `GET /banks`

```json
{ "banks": [{ "name": "Access Bank", "code": "044" }] }
```

### `POST /banks/resolve`

```json
// Request
{ "accountNumber": "string", "bankCode": "string" }
// Response
{ "accountName": "string" }
```

### `POST /payouts/transfer`

```json
// Request
{ "accountNumber": "string", "bankCode": "string", "accountName": "string", "amount": 50000, "reason": "string?" }
// Response
{ "status": "string", "transferCode": "string", "reference": "string" }
```

### `POST /payouts/transfer/finalize`

```json
// Request
{ "transferCode": "string", "otp": "string" }
// Response
{ "status": "string" }
```

Only needed if Paystack comes back requiring OTP confirmation (live mode); not exercised in test mode.

---

## 11. File upload

### `POST /uploads`

```
Content-Type: multipart/form-data
field: file (png/jpeg/webp, max 5MB)
```

```json
// Response
{ "url": "string" }
```

Used for avatars — should also replace the base64 storage still used today for savings receipts and loan-guarantor documents (not yet, since those flows themselves are still mock). Notice attachments already moved off base64 — see below.

### `POST /uploads/attachment` — **built**

```
Content-Type: multipart/form-data
field: file (png/jpeg/pdf/doc/docx, max 2MB)
```

```json
// Response
{ "url": "string", "name": "string", "size": 12345 }
```

Wider file-type allowlist than `/uploads` above (a Notice Board attachment is realistically a PDF
or Word doc, not just an avatar image), `resource_type: "auto"` on the Cloudinary call so
non-image files store correctly, folder `t-coop/notice-attachments`. Feed the returned `url` into
`POST /notices`' `attachment` field (section 7) — replaces the old approach of inlining the file as
base64 directly into the notice record.

---

## 12. Audit log (super admin only)

### `GET /audit-log`

```json
[
  {
    "id": "string",
    "date": "iso-datetime",
    "activityBy": "string",
    "role": "Super Administrator|Administrator|Member",
    "module": "Authentication|Co-operatives|Members|Savings|Loans|Subscriptions|Notices|Settings|Users",
    "action": "Login|Logout|Create|Update|Delete|Approve|Decline|Payment",
    "resource": "string",
    "status": "Success|Info|Warning|Failed",
    "location": "string",
    "ipAddress": "string"
  }
]
```

**Built.** `AuditLogController` — returns the latest 200 entries platform-wide, newest first. 403s for anyone whose role isn't `super_admin` (checked server-side, not just hidden in the UI). `module`/`action`/`resource` values written by every audit-logging call site **must** exactly match the frontend's fixed `AuditModule`/`AuditAction` enums (`t-coop-app/src/lib/audit-log-data.ts`) — an unrecognized value has no icon mapping on the frontend. `location` is resolved server-side from `ipAddress` via a free geo-IP lookup (ipwho.is), cached in memory per IP; this only runs on this read endpoint, never on the write path (login/logout/profile update stay fast regardless of geo-lookup latency or failures). See `documentation/flows.md` for the full request flow and `documentation/api-conventions.md` for why every log call site needs to stay in sync with the frontend's enums.

---

## 13. Platform settings (super admin only)

Backs three Settings tabs: Fees & Charges, Payment Settings → Account Details, and
Integrations. All three read/write the same singleton row (`platform_fee_settings`,
`id = 1`) — see `documentation/schema-design.md`. Every endpoint here 403s for anyone
whose role isn't `super_admin`.

### `GET` / `PATCH /settings/fees`

```json
{
  "savingsChargeType": "Fixed|Percentage",
  "savingsChargeAmount": 0.25,
  "loansChargeType": "Fixed|Percentage",
  "loansChargeAmount": 1,
  "withdrawalFeePercent": 1
}
```

### `GET` / `PATCH /settings/collection-account`

```json
{ "bankCode": "string", "accountNumber": "string", "accountName": "string?" }
```

The platform's own bank account for receiving payments — same real Paystack
"Verify" flow as everywhere else on the frontend, unrelated to any one
co-operative's account.

### `GET` / `PATCH /settings/integrations`

```json
{
  "paystackEnabled": true,
  "paystackPublicKey": "string?",
  "paystackSecretKey": "string?",
  "paystackWebhookSecret": "string?",
  "flutterwaveEnabled": false,
  "flutterwavePublicKey": "string?",
  "flutterwaveSecretKey": "string?",
  "flutterwaveEncryptionKey": "string?",
  "opayEnabled": false,
  "opayPublicKey": "string?",
  "opaySecretKey": "string?",
  "opayMerchantId": "string?",
  "smsEnabled": false,
  "smsApiKey": "string?",
  "smsSenderId": "string?"
}
```

**Paystack/Flutterwave credentials here are reference only — never wired into a live payment
call.** The real Paystack integration (`t-coop-app/src/app/api/paystack/*`) always reads its keys
from the server environment (`PAYSTACK_SECRET_KEY`), never from values saved here; Flutterwave has
no live payout route handler at all. Don't change that without a deliberate decision — see the
javadoc on `IntegrationSettingsUpdateRequest`. **OPay and SMS are different — both are read live**:
OPay by `PaymentGatewayService` on every subscription checkout (section 8), SMS by `SmsService`
(`com.turontechnologies.tcoop.notice`) whenever a Notice Board post's medium includes "SMS"
(section 7/14) — Termii is the only SMS provider wired up. `smsSenderId` must be an alphanumeric
Sender ID approved in the platform's Termii dashboard, or sends fail with
`SENDER_ID_NOT_APPROVED` (logged, non-fatal — the in-app notification still fires).

**Built.** `PlatformSettingsController`/`PlatformSettings`/`PlatformSettingsRepository`
(`V6__add_collection_account_and_integrations.sql` added the Paystack/Flutterwave columns to the
existing singleton, `V14__opay_integration.sql` added OPay's, `V21__sms_integration.sql` added
SMS's). Every update audit-logged (module `Settings` / action `Update` / resource `Fees & Charges`
/ `Collections Account` / `Integrations`).

**Known gap:** Paystack/Flutterwave secret fields are stored as plain
`NVARCHAR`, not encrypted at rest — acceptable for now since they're
explicitly non-live "reference" values (mirrors the mock's prior
in-memory-only behavior, not a regression), but genuine secret-at-rest
handling (encryption, masked responses) would be needed before this ever
becomes the live credential source.

---

## 14. Notifications — **built** (2026-08-24)

The generic, per-recipient notification feed every real event on the platform fans into —
subscription lifecycle, Notice Board posts, co-op onboarding/status, member added/status,
platform-staff invite acceptance. See `documentation/flows.md`'s "Notifications" section for the
full design rationale (why fan-out happens at write time into one row per recipient, rather than a
shared broadcast row) and the complete list of trigger points.

```ts
// Notification shape
{
  "id": 123, "type": "SUBSCRIPTION_EXPIRING|SUBSCRIPTION_EXPIRED|SUBSCRIPTION_RENEWED|NOTICE_BOARD|COOPERATIVE_WELCOME|COOPERATIVE_STATUS|MEMBER_ADDED|MEMBER_STATUS|PLATFORM_STAFF_JOINED",
  "title": "string", "message": "string",
  "link": "string|null",     // relative frontend path to navigate to on click
  "read": false,
  "createdAt": "iso-instant"
}
```

- `GET /notifications` — the caller's own feed only (never a path parameter — always scoped to
  `authentication.getPrincipal()`), capped at 50, newest first.
- `GET /notifications/unread-count` — `{ "count": 3 }`.
- `PATCH /notifications/{id}/read` — 404 if the notification isn't the caller's own.
- `PATCH /notifications/read-all`.

**Exempted from `SubscriptionGateFilter`** (`/api/v1/notifications/**`, alongside
`/subscriptions/me/**`) — a co-op with a lapsed subscription is exactly the co-op most likely to
have a "your subscription expired" notification sitting unread, and marking it read must not
itself be blocked by the very thing it's telling them about.

**Recipient scoping is structural, never a query-time filter someone could get wrong** — see
`NotificationService`: `notifyCoopAdmin` (a co-op's admin IS the member row whose id equals the
co-op's own id), `notifyCoopEveryone`/`notifyCoopMembersOnly` (fan out to `MemberRepository
.findAllByCooperativeId`), `notifyAllSuperAdmins`. Every call ends up as one row per recipient in
the `notifications` table (`V19__notifications.sql`) — there is no broadcast row, so a member of
co-op A can never see anything addressed to co-op B by construction, not by a role check that could
be bypassed.

**Subscription expiry reminders** run daily at 08:00 UTC (`SubscriptionExpiryReminderJob`, the
first use of `@Scheduled`/`@EnableScheduling` in this codebase) — warns a co-op's admin when
`subscriptionExpiresAt` is within 7 days, then once more (a different notification type) after it
actually lapses. Dedup against re-warning every day this runs is keyed on the co-op's exact current
`subscriptionExpiresAt` (`related_cooperative_id` + `related_expires_at` columns) — a renewal
changes that date, which naturally reopens the door to a fresh warning next cycle.

---

## Not in scope

- **Country/State/City dropdowns** — called directly from the browser against `countriesnow.space` (free, public, keyless). No backend work needed unless you want to bring it in-house later.
