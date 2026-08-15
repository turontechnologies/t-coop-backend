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
{ "membershipId": "AD-0001", "password": "string", "keepLoggedIn": true }

// Response
{
  "token": "string",
  "member": { "id": "AD-0001", "name": "string", "email": "string", "role": "admin", "avatarUrl": "string?" }
}
```

Login is by **membership ID**, not email.

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

### `GET /cooperatives`

Returns list with computed totals (backend computes `totalSavings`, `totalLoans` — don't make the frontend sum records).

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
    "memberCount": 12,
    "totalSavings": 960000,
    "totalLoans": 560000
  }
]
```

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

Same shape as one list item, plus `savingsByType` and `loansByType` breakdowns:

```json
{
  ...coop fields,
  "savingsByType": [{ "name": "Basic Savings", "min": 5000, "max": 10000, "total": 235000 }],
  "loansByType": [{ "name": "Emergency Loan", "interestRate": 5, "durationMonths": 3, "total": 150000 }]
}
```

### `PATCH /cooperatives/:id/status`

```json
{ "status": "Active|Disabled" }
```

---

## 3. Members (scoped to a cooperative)

```ts
// CoopMember shape, returned by all endpoints below
{
  "id": "string", "firstName": "string", "lastName": "string", "email": "string",
  "role": "Admin|Member", "status": "Active|Inactive", "guarantor": "string",
  "country": "string", "state": "string", "city": "string",
  "bankCode": "string", "accountNumber": "string", "accountName": "string"
}
```

- `GET /cooperatives/:id/members` — list. `admin` role should only ever see their own co-op's members.
- `POST /cooperatives/:id/members` — create. Request = above shape minus `id`, plus `membershipId`.
- `POST /cooperatives/:id/members/bulk` — multipart file upload (Excel), same fields per row. Response: `{ "imported": 12, "errors": [{ "row": 3, "message": "string" }] }`.
- `GET /cooperatives/:id/members/:memberId` — detail.
- `PATCH /cooperatives/:id/members/:memberId` — update `firstName, lastName, email, role, guarantor, country, state, city, bankCode, accountNumber, accountName`.
- `PATCH /cooperatives/:id/members/:memberId/status` — `{ "status": "Active|Inactive" }`.

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

**Built.** `ProfileController`/`ProfileDto`/`ProfileUpdateRequest` — server-side validation mirrors the frontend's zod schema exactly (10-digit account number, 11-digit NIN, valid email, etc.), so a request that passes client-side validation never fails here. GET is a single `findById` (sub-100ms locally). Every update is audit-logged (`Profile` / `Update Profile`). Validation failures return `{"error": "combined human-readable message"}` via the new `GlobalExceptionHandler` — see api-conventions.md.

---

## 5. Savings

### `GET /savings/types`

```json
[{ "name": "Basic Savings", "min": 5000, "max": 10000 }]
```

Static catalog (3 types today) — fine as a hardcoded config endpoint or DB table.

### `GET /cooperatives/:id/savings`

List savings records. Supports query params `?memberId=&savingsType=&status=&from=&to=`.

```json
[
  {
    "id": "string",
    "memberId": "string",
    "memberName": "string",
    "savingsType": "string",
    "amount": 90000,
    "balanceAfter": 90000,
    "method": "Paystack|Manual Upload",
    "transactionId": "string",
    "date": "2025-07-09",
    "status": "Success|Pending|Failed",
    "receiptUrl": "string?"
  }
]
```

### `GET /savings/:recordId`

Single record, same shape.

### `POST /cooperatives/:id/savings` (admin manual/teller upload)

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

### `POST /savings/requests` (member self-service)

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

### `GET /cooperatives/:id/savings/requests`

List, same shape as above plus `resolvedAt`. `admin` sees their own co-op's; `super_admin` can pass no `:id` filter to see all.

### `PATCH /savings/requests/:id`

```json
// Request
{ "status": "Approved|Declined" }
```

On `Approved` + `type: Withdrawal`: backend must trigger a real payout via §10 (`/payouts/transfer`) to the member's saved bank details before marking it Approved. If the transfer fails, the request must stay `Pending` and the error surfaced back to the client — don't mark Approved if money didn't move.

### `GET /savings/summary?coopId=`

```json
[{ "name": "Basic Savings", "min": 5000, "max": 10000, "total": 235000 }]
```

Used for the "Members Savings" breakdown table.

---

## 6. Loans

### `GET /loans/types`

```json
[
  {
    "name": "Emergency Loan",
    "interestRate": 5,
    "maxAmount": 50000,
    "durationMonths": 3,
    "eligibilityPercent": 300
  }
]
```

### `GET /loans/eligibility?memberId=&loanType=`

```json
{ "eligibleAmount": 150000 }
```

Computed as `min(maxAmount, totalSavings * eligibilityPercent / 100)`.

### `POST /cooperatives/:id/loans` (member requests a loan)

```json
// Request
{ "loanType": "string", "amount": 50000, "guarantorId": "string" }
// Response — status starts "Awaiting Guarantor"
{
  "id": "string", "memberId": "string", "memberName": "string", "loanType": "string",
  "amount": 50000, "interestRate": 5, "durationMonths": 3, "numberOfRepayments": 3,
  "monthlyRepayment": 17500, "totalRepayment": 52500, "guarantorName": "string",
  "date": "iso-date", "status": "Awaiting Guarantor|Awaiting Admin|Active|Completed|Rejected",
  "repaymentsMade": 0
}
```

### `PATCH /loans/:id/guarantor-response`

```json
// Request
{ "decision": "Accepted|Rejected", "documentFile": "multipart file?" }
```

`Accepted` → status moves to `Awaiting Admin`. `Rejected` → status moves to `Rejected`.

### `PATCH /loans/:id/decision` (admin)

```json
// Request
{ "decision": "Approved|Rejected", "rejectionReason": "string?" }
```

On `Approved`: trigger a real payout via §10 to the member's bank details, same "don't mark Approved unless the transfer succeeded" rule as savings withdrawals. Moves status to `Active` (or `Rejected`).

### `GET /cooperatives/:id/loans`

List, same shape as the request response above.

### `GET /loans/:id`

Single record detail.

### `GET /loans/:id/repayment-schedule`

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

**Note**: the current frontend only _displays_ a computed schedule and never records an actual repayment — there's no "make a repayment" action anywhere in the UI today. Flagging this as a real gap: decide with product whether repayments are auto-deducted (wallet), manually recorded by admin, or out of scope for this phase, then add a `POST /loans/:id/repayments` endpoint accordingly.

---

## 7. Notices

```ts
// Notice shape
{
  "id": "string", "type": "General|Meeting Notice|Meeting Minutes",
  "title": "string", "message": "string",
  "recipient": "All Members|All Admins|All Members & Admins",
  "medium": "Email|SMS|Email & SMS",
  "meetingDate": "iso-date?",           // only for "Meeting Notice"
  "attachment": { "name": "string", "url": "string", "size": 12345 } | null,
  "sendAt": "iso-datetime",             // future = scheduled, not yet sent
  "createdByName": "string", "createdByRole": "super_admin|admin|member",
  "createdAt": "iso-datetime"
}
```

- `GET /notices` — list. `member` role should only receive notices already sent (`sendAt <= now`) and addressed to them (`recipient` includes their role).
- `POST /notices` — create. Request = above minus `id/createdByName/createdByRole/createdAt`. Attachment as multipart file, not base64.
- `GET /notices/:id` — detail.
- `DELETE /notices/:id` — also deletes its replies.
- `POST /notices/:id/resend` — sets `sendAt` to now.
- `GET /notices/:id/replies` — `[{ "id", "noticeId", "authorId", "authorName", "authorRole", "authorAvatarUrl", "message", "createdAt" }]`.
- `POST /notices/:id/replies` — `{ "message": "string" }`.
- `POST /notices/:id/read` — marks read for the current user (for read-receipt tracking across devices).

---

## 8. Subscriptions (super_admin only)

```ts
// CoopSubscriptionPayment shape
{
  "id": "string", "paymentRef": "string", "amountPaid": 300000,
  "method": "Manual|Paystack", "date": "iso-date", "narration": "string",
  "status": "Active|Overdue" // the co-op's subscription standing as of this payment
}
```

### `GET /subscriptions`

List every co-op's subscription standing. Supports `?status=&search=&from=&to=` (date range filters on last-payment date).

```json
[
  {
    "coopId": "string",
    "coopName": "string",
    "revenueEarned": 900000, // sum of all payments for this co-op
    "subscriptionFee": 300000, // recurring fee amount
    "lastPaymentDate": "iso-date",
    "status": "Active|Overdue" // most recent payment's status
  }
]
```

### `GET /subscriptions/summary`

```json
{ "mgtFeesReceived": 1200000 }
```

Sum of `revenueEarned` across every co-op.

### `GET /cooperatives/:id/subscriptions`

Full payment history for one co-op, newest first — array of `CoopSubscriptionPayment`.

### `POST /cooperatives/:id/subscriptions`

```json
// Request
{ "amountPaid": 75000, "narration": "string" }
// Response: the created CoopSubscriptionPayment (paymentRef and date generated server-side, status "Active")
```

This is a manual record of money already received (bank transfer, cheque, etc.) — not a payment gateway call. If a real online-collection flow is added later, `method` already supports `"Paystack"` alongside `"Manual"`.

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

Currently only used for avatars — should also replace the base64 storage used today for savings receipts, loan-guarantor documents, and notice attachments (all currently stored as base64 data URLs directly in records, which won't scale).

---

## Not in scope

- **Country/State/City dropdowns** — called directly from the browser against `countriesnow.space` (free, public, keyless). No backend work needed unless you want to bring it in-house later.
