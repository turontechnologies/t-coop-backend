# T-Coop API Contracts

The contract between `t-coop-backend` and its two clients (the `t-coop` web app and
`t-coop-mobile`) — the single maintained copy. Sections marked **built** are live. All
endpoints except **Auth** (and the public invite links) require `Authorization: Bearer <token>`. Money amounts are in Naira (numbers, not kobo)
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

Sends the OTP by email server-side. The OTP is never returned in the response.

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

## 2. Cooperatives

**No longer super_admin-only** — every endpoint below uses the same `requireCoopAccess` pattern
as §3: a `super_admin` caller can act on any co-op; an `admin` caller only their own (`{id}` must
equal their own `cooperativeId`, checked server-side, `{id}` in the URL is never trusted blindly).
This widened over two sessions — `GET/PATCH /cooperatives/:id` were still super_admin-only until
the admin-Settings work below needed them and it was caught via a live test against the real
backend (the admin-facing frontend forms 404'd/403'd the first time they were pointed at real
data — fixed same session, not a pre-existing bug that shipped).

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
    "withdrawalFeePercent": 1.5,
    "bankCode": "string?",
    "accountNumber": "string?",
    "accountName": "string?",
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
Members are edited and enabled/disabled through `PATCH /cooperatives/:id/members/:memberId` and
`.../status` (§3).

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

`currency` and `withdrawalFeePercent` are optional on this same request — omitted/null means
"don't touch" (the super admin's own edit form never sends them; the admin's Settings → Currency
and → Withdrawal Fee tabs do, via a GET-then-merge-then-PATCH of the whole record, same pattern as
everything else in this codebase that shares one update endpoint across two forms).

Since the co-op's admin identity lives on the same `Member` row the admin logs in and edits
their own profile as (see above), this endpoint also writes `adminFirstName`/`adminLastName`/
`contactEmail`/`contactPhone` through to that row — a super admin's edit here is what the
admin sees reflected in their own portal, immediately, with no separate sync step.

### `PATCH /cooperatives/:id/bank-account`

The co-op's own receiving account (separate from any individual member's, including the admin's
own personal one on `/profile`). No "unset" — `bankCode`/`accountNumber` are required.

```json
// Request
{ "bankCode": "string", "accountNumber": "10 digits", "accountName": "string?" }
// Response: the updated Cooperative (same shape as GET /cooperatives item)
```

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

**All built.** `SavingsController` serves the read side (types, records); `SavingsSelfServiceController`
serves deposits, "Upload Teller", and withdrawal requests/decisions.

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

### `POST /cooperatives/:id/savings/types` / `PATCH .../types/:typeId` / `PATCH .../types/:typeId/status` — **built**

The management UI this file's older note said didn't exist yet — it does now, `requireCoopAccess`
(admin manages their own, super admin any). Request/response both use `minAmount`/`maxAmount` on
the way in (matching `SavingsTypeCreateRequest`) but `min`/`max` on the way out (matching the DTO
above) — a real, intentional naming mismatch between request and response, not a typo.

```json
// POST/PATCH request
{ "name": "string", "minAmount": 5000, "maxAmount": 10000 }
// PATCH .../status request
{ "status": "Active|Inactive" }
// All three respond with the updated row, same shape as the GET .../types list item above
```

No delete endpoint — deliberate, matching the "never destroy a savings type with live records
against it" convention; disable it via the status endpoint instead.

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

### `POST /cooperatives/:id/savings/deposits/initialize` / `.../deposits/confirm` — **built**

A member's online deposit. Initialize fixes the amount and picks the gateway (the co-op's own
Paystack, else its own Flutterwave, else the platform's Paystack — see §13 "Co-op payment
gateways"); the client opens that gateway's checkout with `publicKey`, then confirms.

```json
// initialize request
{ "savingsTypeId": "uuid", "amount": 90000 }
// initialize response
{ "reference": "SAV-…", "amount": 90000, "gateway": "Paystack|Flutterwave",
  "publicKey": "string", "chargeAmount": 225, "netAmount": 89775 }
// confirm request
{ "reference": "SAV-…" }
// confirm response
{ "record": { /* savings record */ }, "grossAmount": 90000, "chargeAmount": 225 }
```

Confirm verifies the reference against the gateway server-side (`402` if it isn't paid in full).
The record's `method` is the gateway name; `amount` is net of the savings charge.

### `POST /cooperatives/:id/savings/deposits/manual` ("Upload Teller") — **built**

```json
{ "memberId": "string", "savingsTypeId": "uuid", "amount": 90000, "receiptUrl": "string?" }
```

Admin/co-op staff record a deposit paid offline; `method` is `Manual Upload`.

### `POST` / `GET /cooperatives/:id/savings/withdrawals` — **built**

```json
// POST (member) request
{ "savingsTypeId": "uuid?", "amount": 50000, "note": "string?" }
```

`GET` lists the co-op's withdrawal requests (staff) or the caller's own (member).

### `PATCH /cooperatives/:id/savings/withdrawals/:requestId` — **built**

```json
{ "status": "Approved|Declined" }
```

Admin / co-op staff. **Approving pays the member out immediately, server-side** — a Paystack
transfer of `netAmount` (amount minus the withdrawal fee) to the member's saved bank account,
from the co-op's own Paystack if connected, else the platform's (same rule as loan payouts).
The reference is `wd-<requestId>`, so a retry can't pay twice. Only if Paystack accepts the
transfer is the request `Approved` and a negative savings record (`method: Paystack`,
`transactionId` = the reference) written; otherwise it stays `Pending` — `409` no bank account /
no Paystack set up, `502` Paystack refused. Only `Withdrawal` requests can be decided here.

---

## 6. Loans

`LoanController` serves the read side; `LoanSelfServiceController` the application, guarantor, and
officer approval chain; `LoanRepaymentController` repayments.

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
    "maxAmount": 200000,
    "repaymentInterval": "Weekly|Monthly|Quarterly",
    "numberOfRepayments": 3,
    "interestType": "Percentage|Fixed",
    "interestRate": 5,
    "status": "Active|Inactive",
    "earnings": 3500
  }
]
```

`earnings` ("Earnings on Loan") = `sum(totalRepayment - amount)` over every non-`Rejected` loan
of that type — real interest collected, not illustrative like Savings' 2% figure, matching the
frontend's `coopLoansBySummaryType` math exactly.

### `POST /cooperatives/:id/loans/types` / `PATCH .../types/:typeId` / `PATCH .../types/:typeId/status` — **built**

Same pattern as Savings types above — `requireCoopAccess`, no delete, disable via status instead.
Unlike Savings, request and response field names for the numeric/interest fields **do** differ:
the request uses `numberOfInstallments`/`interestAmount`, the response (above) uses
`numberOfRepayments`/`interestRate` — bridge this in the frontend service layer, don't rename one
side to match the other without checking both `LoanType`/`LoanTypeCreateRequest` and
`LoanTypeSummaryDto` first.

```json
// POST/PATCH request
{
  "name": "string",
  "durationMonths": 3,
  "maxAmount": 200000,
  "repaymentInterval": "Weekly|Monthly|Quarterly",
  "numberOfInstallments": 3,
  "interestType": "Percentage|Fixed|NoInterest",
  "interestAmount": 5
}
// interestAmount: a percentage for Percentage, a flat amount for Fixed, omitted for NoInterest.
// eligibilityPercent is optional and no longer used — the loan mode sets the limit (below).
// PATCH .../status request
{ "status": "Active|Inactive" }
```

### `GET /cooperatives/:id/loans` — **built**

List, newest first. Every filter optional: `?memberId=&type=&status=&from=&to=` (`type` matches
a loan type's name, not id).

```json
[
  {
    "id": "uuid", "memberId": "string", "memberName": "string", "loanType": "string",
    "amount": 40000, "interestRate": 5, "durationMonths": 3, "numberOfRepayments": 3,
    "monthlyRepayment": 14000, "totalRepayment": 42000, "guarantorName": "string",
    "date": "iso-date",
    "status": "Awaiting Guarantor|Awaiting Vice President|Awaiting Treasurer|Awaiting Secretary|Active|Completed|Rejected",
    "repaymentsMade": 3, "guarantorDocumentUrl": "string?", "rejectionReason": "string?",
    "disbursedAmount": 39000, "disbursementReference": "loan-<uuid>?", "disbursedAt": "iso-datetime?"
  }
]
```

### `GET /loans/:recordId` — **built**

Single record, same shape as one list item above.

### Loan modes, limits and interest — **built** (`LoanEligibility`, V41)

Every application picks a **loan mode**:

| Mode | Limit | Guarantor | Savings |
|---|---|---|---|
| `TimesTwo` (default) | up to **half** the member's savings (savings must be at least twice the loan) | required | untouched |
| `Within` | up to the member's savings | none — goes straight to the Vice President | the payout is deducted from savings (`method: Loan Disbursement`); each repayment's principal is credited back (`method: Loan Repayment`) |

Both are capped by the loan type's `maxAmount` and go through the officer approval chain.
**Every loan charges its loan type's interest** — `Percentage` (of the amount), `Fixed` (a flat
amount), or none for `NoInterest`; the record's `interestRate` is the effective percentage. A
Within loan is re-checked against the member's savings at payout. Every co-op has a
**Household Loan** type (V41; 12 months, 5%, up to 300,000 — editable).

### `POST /cooperatives/:id/loans` (member requests a loan) — **built**

```json
// Request
{ "loanTypeId": "uuid", "amount": 50000, "loanMode": "TimesTwo|Within", "guarantorMemberId": "string?" }
// Response — status "Awaiting Guarantor" (TimesTwo) or "Awaiting Vice President" (Within)
```
Response shape matches the list item above, plus `loanMode`. For `TimesTwo` the guarantor must
be an **active member of the same co-op** and not the applicant (`400` otherwise); `409` when
the amount is over the mode's limit. This is the only place a guarantor is
named — adding a member needs none.

### `PATCH /cooperatives/:id/loans/:loanId/guarantor-response` — **built**

```json
{ "decision": "Accepted|Rejected", "documentUrl": "string?" }
```

Named guarantor only. `Accepted` → `Awaiting Vice President` (the VP, or the admin if that
office is vacant, is notified). `Rejected` → `Rejected`.

### Officer approval chain — **built** (V40)

Every co-op has four fixed officer roles — President, Vice President, Treasurer, Secretary —
as co-op roles with `office` set (`PRESIDENT|VICE_PRESIDENT|TREASURER|SECRETARY`). They're
created with the co-op (V40 backfilled existing ones), assigned through User Management like any
role, held by **one member each**, and can't be renamed, deactivated, or deleted (`409`). Their
permissions are editable.

A loan moves **Guarantor → Vice President → Treasurer → Secretary**. At each step only that
office's holder can approve or reject. If the office is vacant — or its holder is the loan's
borrower or guarantor — the co-op **admin covers** that step. The **President** can reject (not
approve) at any open stage, including while it awaits the guarantor. Nobody acts on a loan they
borrowed or guaranteed. Super admins don't act on loans.

### `GET /cooperatives/:id/loans/:loanId/approvals` — **built**

```json
{
  "chain": [{ "office": "VICE_PRESIDENT", "label": "Vice President", "holderName": "string|null" }],
  "trail": [{ "office": "VICE_PRESIDENT", "label": "Vice President", "decision": "Approved|Rejected",
              "memberName": "string", "covering": false, "note": "string?", "actedAt": "iso-datetime" }],
  "currentStep": "TREASURER|null",
  "canApprove": true, "canReject": true,
  "actingAs": "Treasurer|Admin (covering for Treasurer)|President|null",
  "netDisbursement": 39000
}
```

Borrower, guarantor, admin, and co-op staff can read it; `canApprove`/`canReject`/`actingAs` are
for the caller.

### `PATCH /cooperatives/:id/loans/:loanId/decision` — **built**

```json
{ "decision": "Approved|Rejected", "rejectionReason": "string?" }
```

Acts on the current step as the caller's office (`403` if it isn't theirs). Approving at Vice
President / Treasurer moves to the next office and notifies it. **Approving at Secretary pays
the loan out immediately**: a server-side Paystack transfer of principal minus loan charges to
the borrower's saved bank account, from the co-op's own Paystack (Settings → Integrations) if
connected, else the platform's. The transfer reference is `loan-<loanId>`, so a retry can never
pay twice. Only if Paystack accepts the transfer (`success`/`pending`) does the loan become
`Active`; otherwise nothing changes and the error is returned (`409` no bank account / no
Paystack set up, `502` Paystack refused — including transfer-OTP being switched on). Every
decision is stored in `loan_approvals`.

### `GET /cooperatives/:id/members/:memberId/loan-history` — **built**

A member's full borrowing record, for the officers reviewing a request (co-op staff) or the
member themselves (`403` otherwise):

```json
{
  "memberId": "MB-0003", "memberName": "string", "totalSavings": 598500,
  "summary": {
    "activeLoans": 1, "completedLoans": 2, "rejectedLoans": 0, "pendingLoans": 1,
    "outstandingBalance": 96250, "totalBorrowed": 300000, "totalRepaid": 210000,
    "repaymentsMade": 14, "onTimeRepayments": 12, "lateRepayments": 2, "onTimeRate": 86,
    "overdueInstallments": 0, "lastRepaymentDate": "iso-date?"
  },
  "loans": [{
    "id": "uuid", "loanType": "string", "loanMode": "TimesTwo|Within", "status": "string",
    "amount": 100000, "totalRepayment": 105000, "repaid": 8750, "outstanding": 96250,
    "numberOfRepayments": 12, "repaymentsMade": 1, "overdueInstallments": 0,
    "appliedOn": "iso-date", "paidOutOn": "iso-date?", "nextDueDate": "iso-date?",
    "repayments": [{ "installmentNumber": 1, "amount": 8750, "method": "string",
                     "paidOn": "iso-date", "dueDate": "iso-date", "daysLate": 0 }]
  }]
}
```

Installments fall due monthly from the payout date (`LoanSchedule`); a repayment is on time if
paid on or before its due date.

### Loan repayments — **built** (`LoanRepaymentController`)

Every repayment pays exactly one fixed installment; the amount always comes from the server.

| Endpoint | Who | What |
|---|---|---|
| `GET /cooperatives/:id/loans/:loanId/repayments/next` | borrower, staff | `{ "installmentNumber": 2, "amount": 3500 }` |
| `POST .../repayments/initialize` | borrower | `{ "reference": "LNRPY-…", "installmentNumber": 2, "amount": 3500, "gateway": "Paystack\|Flutterwave", "publicKey": "string" }` — same gateway rule as savings deposits |
| `POST .../repayments/confirm` `{ "reference" }` | borrower | verifies with the gateway, records it → `{ "repayment": {…}, "loan": {…} }` |
| `POST .../repayments/manual` | admin / co-op staff | records the next installment as paid offline (`Manual Upload`) |
| `GET .../repayments` | borrower, staff | `[{ "id", "loanId", "installmentNumber", "amount", "method", "transactionId", "date", "status" }]` |

The final installment moves the loan to `Completed`. The repayment *schedule* isn't an endpoint —
clients derive it from the loan's `amount`/`numberOfRepayments`/`totalRepayment`/`date`/
`repaymentsMade` (`generateRepaymentSchedule` in the web app's `loans-data.ts`).

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
- `POST /notices` — create. **A `super_admin` caller may only use `recipient: "All Admins"`** — 403
  otherwise (2026-08-24); reaching members directly is left to the co-op's own admin. `sendAt` is
  the final computed ISO instant (frontend resolves "now" vs
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

## 8c. Financial report — **built** (`FinancialReportController`, 2026-10-28)

### `GET /cooperatives/:id/reports/financial?from=YYYY-MM-DD&to=YYYY-MM-DD`

Co-op admin, or co-op staff whose role grants **Reports** (officers get `Reports::read` by
default — V41). `403` otherwise; `400` for a bad or reversed range. Defaults to 1 January → today.

```json
{
  "cooperativeId": "string", "cooperativeName": "string", "currency": "NGN",
  "from": "iso-date", "to": "iso-date", "generatedAt": "iso-datetime",
  "savings": { "deposits": 0, "depositCount": 0, "withdrawals": 0, "withdrawalCount": 0,
               "withinLoanDebits": 0, "withinLoanCredits": 0, "netChange": 0, "closingBalance": 0 },
  "loans": { "applications": 0, "paidOut": 0, "principalPaidOut": 0, "netPaidOut": 0, "rejected": 0,
             "repaymentsCollected": 0, "repaymentCount": 0, "activeLoans": 0,
             "outstandingBalance": 0, "overdueLoans": 0, "overdueOutstanding": 0 },
  "income": { "interestEarned": 0, "loanCharges": 0, "savingsCharges": 0, "withdrawalFees": 0, "total": 0 },
  "monthly": [{ "month": "2026-10", "deposits": 0, "withdrawals": 0, "loansPaidOut": 0, "repayments": 0 }],
  "byLoanType": [{ "loanType": "string", "loansPaidOut": 0, "principal": 0, "repaid": 0, "outstanding": 0 }],
  "overdue": [{ "loanId": "uuid", "memberId": "string", "memberName": "string", "loanType": "string",
                 "outstanding": 0, "overdueInstallments": 1, "oldestDueDate": "iso-date" }]
}
```

Flows cover the range; `activeLoans`/`outstandingBalance`/overdue figures and `closingBalance`
(as of `to`) are balances. Interest earned is each repayment's interest share
((totalRepayment − amount) / totalRepayment). Savings charges come from online deposits
(paid − credited); withdrawal fees from approved requests.

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

**Every credential here is read live.** Paystack/Flutterwave/OPay by `PaymentGatewayService` on
every subscription checkout (section 8); Paystack also by `PaystackUtilityController`
(`GET /paystack/banks`, `POST /paystack/resolve-account`, which back every bank picker on web and
mobile) and by `CoopGatewayResolver` for members' savings deposits, loan repayments, and loan and
withdrawal payouts in any co-op that hasn't connected its own gateway (see "Co-op payment
gateways" below). No payment key is read from an environment variable. SMS is read by `SmsService`
(`com.turontechnologies.tcoop.notice`) whenever a Notice Board post's medium includes "SMS"
(section 7/14) — Termii is the only SMS provider wired up. `smsSenderId` must be an alphanumeric
Sender ID approved in the platform's Termii dashboard, or sends fail with
`SENDER_ID_NOT_APPROVED` (logged, non-fatal — the in-app notification still fires).

**Built.** `PlatformSettingsController`/`PlatformSettings`/`PlatformSettingsRepository`
(`V6__add_collection_account_and_integrations.sql` added the Paystack/Flutterwave columns to the
existing singleton, `V14__opay_integration.sql` added OPay's, `V21__sms_integration.sql` added
SMS's). Every update audit-logged (module `Settings` / action `Update` / resource `Fees & Charges`
/ `Collections Account` / `Integrations`).

**Known gap:** secret fields here are stored as plain `NVARCHAR`, not encrypted at rest, and
this endpoint returns them unmasked to the super admin. The co-op endpoint below masks them in
responses; neither encrypts at rest yet.

### `GET` / `PATCH /cooperatives/{id}/integrations` — co-op payment gateways

A co-op's own Paystack/Flutterwave account (co-op admin's Settings → Integrations). When one is
on, that co-op's members' savings deposits and loan repayments are paid into it instead of the
platform's account, and its loan and withdrawal payouts are sent from it (Paystack only).

`GET` response:

```json
{
  "paystackEnabled": true,
  "paystackPublicKey": "pk_live_…",
  "paystackSecretKeySet": true,
  "flutterwaveEnabled": false,
  "flutterwavePublicKey": "",
  "flutterwaveSecretKeySet": false,
  "updatedAt": "2026-10-27T10:00:00"
}
```

Secret keys are **never returned** — only whether one is saved.

`PATCH` request:

```json
{
  "paystackEnabled": true,
  "paystackPublicKey": "pk_live_…",
  "paystackSecretKey": "",
  "flutterwaveEnabled": false,
  "flutterwavePublicKey": "",
  "flutterwaveSecretKey": ""
}
```

A blank secret keeps the saved one. Turning a gateway on requires both keys (new or saved);
`400` if either is missing, has the wrong prefix (`pk_`/`sk_` for Paystack,
`FLWPUBK`/`FLWSECK` for Flutterwave), or if one is a test key and the other live.

**Access:** the co-op's own admin, or co-op staff whose role grants Settings → Integrations
(`read` for `GET`, `write` for `PATCH`); `403` for everyone else, super admins included — these
keys move money into the co-op's account, not the platform's. Updates audit-logged (module
`Settings` / action `Update` / resource `Payment Integrations`).

**How payments use it** (`CoopGatewayResolver`): the savings-deposit and loan-repayment
`…/initialize` endpoints pick the co-op's Paystack if on, else its Flutterwave, else the
platform's Paystack, and return `{ …, "gateway": "Paystack|Flutterwave", "publicKey": "…" }`.
The gateway and key owner are stored on the payment intent, so `…/confirm` verifies against
the same account even if the admin changes settings in between. `409` if none is available.
Recorded `method` is the gateway name (`Paystack`/`Flutterwave`), or `Manual Upload`.

**Built.** `coopgateway` package, `V39__cooperative_payment_gateways.sql`.

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
  "id": 123, "type": "SUBSCRIPTION_EXPIRING|SUBSCRIPTION_EXPIRED|SUBSCRIPTION_RENEWED|NOTICE_BOARD|COOPERATIVE_WELCOME|COOPERATIVE_STATUS|MEMBER_ADDED|MEMBER_STATUS|PLATFORM_STAFF_JOINED|COOP_ROLE_ASSIGNED|COOP_ROLE_REMOVED",
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
`NotificationService`: `notify` (single recipient — used directly by Notice Board's own fan-out
logic in `NoticeController`, not a `NotificationService` convenience method), `notifyCoopAdmin` (a
co-op's admin IS the member row whose id equals the co-op's own id), `notifyAllSuperAdmins`. Every
call ends up as one row per recipient in
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

## 15. Co-op staff / admin's User Management — **built** (2026-08-24)

Admin's own Settings → User Management. **Deliberately not shaped like Platform staff (§13's
sibling, `com.turontechnologies.tcoop.platformstaff` — search `flows.md` for it, not documented
in this file)** — no invite-by-email, no new account, no `Invited` status. An admin first adds
the person as a regular member (§3, already real), then assigns them a `CoopRole` here to elevate
their access. They keep their existing membership ID/password. `com.turontechnologies.tcoop.
coopstaff` package. `requireCoopAccess` throughout (admin: own co-op only; super_admin: any).

### `GET/POST /cooperatives/:id/roles`, `PATCH .../roles/:roleId`, `PATCH .../roles/:roleId/status`, `DELETE .../roles/:roleId`

Identical shape/semantics to Platform staff's role endpoints, just co-op-scoped and permissions
drawn from a different, smaller module list (`COOP_PERMISSION_MODULES` on the frontend — matches
admin's own nav items, since a co-op staff member's nav is filtered against *that* list, not the
super-admin one).

```json
// CoopRole
{ "id": "uuid", "name": "string", "permissions": ["Members Directory", "Notice Board", "..."], "status": "Active|Inactive", "dateAdded": "iso-datetime" }
```

`DELETE` returns 409 if any member currently has this role (`MemberRepository.countByCoopRoleId`)
— reassign or remove them first, same guard as Platform staff's roles.

### `GET /cooperatives/:id/users`

Members of this co-op who currently have a `CoopRole` assigned (`coopRoleId IS NOT NULL`).

```json
[{ "id": "MB-001", "name": "string", "email": "string", "role": "string (CoopRole name)", "status": "Active|Inactive" }]
```

`status` is the member's own real account status — there's no `Invited` pseudostate here, unlike
Platform staff's `PlatformUser.status`.

### `PATCH /cooperatives/:id/users/:memberId/role`

Assigns or re-assigns — `memberId` must already be a member of this co-op (add them via §3 first)
and can't be the co-op's own admin row (`memberId == :id` → 409, they already have full access).

```json
// Request
{ "roleId": "uuid" }
// Response: same shape as a GET .../users row
```

Sends the member a `COOP_ROLE_ASSIGNED` notification (§14). Their `permissionModules` (resolved
from this `CoopRole`, see `AuthController.toDto`) only take effect on their **next login** — not
retroactively on an already-issued JWT.

### `DELETE /cooperatives/:id/users/:memberId`

Revokes staff access — clears `coopRoleId` only. **Never deletes the member** or touches their
savings/loans/membership; they simply go back to being a regular member. Sends a
`COOP_ROLE_REMOVED` notification.

---

## Not in scope

- **Country/State/City dropdowns** — called directly from the browser against `countriesnow.space` (free, public, keyless). No backend work needed unless you want to bring it in-house later.
