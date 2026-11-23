# Fee collection (`tenantdata/fee`)

Bills students from the fee set-up (`tenantdata/operations`: heads, settings, instalments, structures, concession types),
takes counter payments, issues receipts, tracks dues and concessions. API contract and screen mapping:
`APiReqDoc/06_FEES_FINANCE.md`. Tables: tenant `V29__fee_ledgers_payments_concessions.sql`; permissions: master `V40`.

## 1. Model

| Table | What |
|---|---|
| `fee_ledger` | One per student and academic year (= the invoice, `INV/26/00001`). Class / section / structure as last billed |
| `fee_ledger_line` | One amount owed: `STRUCTURE` (head × instalment) or `CHARGE` (one-off). `amount`, `concession`, `paid`, `pending` (cheques), `due_date`, `ACTIVE` / `CANCELLED` |
| `fee_payment` | Money received = the receipt (`RCP/26/00001`, prefix from fee settings). `SUCCESS`, `PENDING_CLEARANCE`, `BOUNCED`, `REVERSED` |
| `fee_payment_allocation` | How much of a payment went to which line |
| `fee_concession_request` | Per student and year (`CON/26/00001`): `PENDING` → `APPROVED` / `REJECTED`; `APPROVED` → `REVOKED` |
| `fee_line_concession` | What an approved request gave each line (so revoke / cancel give back exactly that) |
| `fee_reminder` | Reminders sent per ledger (for "last reminded") |
| `fee_number_sequence` | Running numbers per kind and year (`INSERT … ON CONFLICT DO UPDATE … RETURNING`) |

All money is **paise** (`BIGINT`). Line due = amount − concession − paid − pending; a cancelled line owes nothing.
Line status (derived): `PAID` nothing due · `OVERDUE` due date passed · `PARTIAL` something paid · `UPCOMING`.
Who did what is stored on the rows (`collected_by`, `status_changed_by`, `cancelled_by`, `requested_by`, `reviewed_by`, `revoked_by`); there is no tenant audit log.

## 2. Rules

**Billing** (`FeeInvoiceService.generate`)
- Roll: `ADMITTED` / `ACTIVE` enrolments of the year. Structure: the class's `ACTIVE` one for `NEW_ADMISSION` (no enrolment
  in an earlier year) or `EXISTING`, else `ALL`.
- Compulsory, active heads only. Year amount = structure amount × `frequency.timesPerYear()`, split by instalment percent,
  the last instalment taking the rounding; `ONE_TIME` heads fall in instalment 1. Optional heads are added as charges.
- Only missing `(head, instalment)` lines are added (unique index), so re-running is safe and a cancelled line stays cancelled.
- Approved concessions are applied to the new lines. `dryRun` saves nothing (concessions are estimated).
- Instalment number and name are copied onto the line: replacing the instalment plan deletes `fee_installment` rows
  (`installment_id` becomes null, the line keeps its number and name).

**Payments** (`FeePaymentService`)
- Lock the ledger (`FeeLedgerRepository.lock`, `SELECT … FOR UPDATE`) before reading its lines; everything that changes
  lines (payments, cheques, reversals, concessions, charges, cancels, billing an existing ledger) does the same.
- Allocation: explicit (lines of this ledger, ≤ due, sum = amount) or oldest due first. No overpayment. Fee settings
  `allowPartialPayment = false` → every line touched is paid whole.
- `CHEQUE` → `PENDING_CLEARANCE`, added to `pending_paise`. Cleared → moved to `paid_paise`, `realized_on` = clearance date.
  Bounced → released. `DD` and the others are `SUCCESS` at once.
- Reverse (`FEE_APPROVE`): `SUCCESS` only; subtracts the allocations from `paid_paise`; the row stays as `REVERSED`.
- "Collected" always means `SUCCESS` by `realized_on`. The day book (by `paid_on`) counts pending cheques as received.

**Concessions** (`FeeConcessionService`)
- Value from the type unless overridden. One live request per student, year and type.
- Type `requiresApproval = false` → approved on creation; otherwise approver ≠ requester.
- `FeeMath.concessionShares`: only `ACTIVE` `STRUCTURE` lines of the type's head (or all heads) with something left to pay.
  PERCENT: round(amount × % ÷ 100) per line minus what it already got, capped by what is left. FIXED: what is left of the
  amount, latest due dates first.
- Revoke: lines due on or after `effectiveFrom` give back what the request gave them.

**Late fine** (`FeeMath.lateFine`): from fee settings (`NONE`, `FIXED`, `PER_DAY`, `PERCENT` of the line's due; grace days;
cap). Shown on lines and in reminders, **never charged automatically**: the office adds it as a charge.

**Reminders** (`FeeReminderService` → `FeeNotifier` → notification outbox): overdue ledgers get `FEE_OVERDUE`, the others
`FEE_DUE_REMINDER` for the next due date; guardians from `student_guardian`; dedupe key
`FEE_REMINDER:<ledger>:<date>:<channel>:<person>` = once a day per guardian and channel. Receipt messages use
`FEE_PAYMENT_RECEIVED` and a fresh key per send.

## 3. Access

| Endpoint group | Needs |
|---|---|
| Lists, summary, reports, day book, defaulters | `FEE_READ` + `FEE_SCOPE_ALL` |
| One ledger, a receipt, `GET /students/{id}/fees` | `FEE_READ` + may see that student (`DataAccessService.canAccessStudent`, parents for their children) |
| Billing, charges, cancel, collect, cheque status, reminders, ask for a concession | `FEE_MANAGE` + `FEE_SCOPE_ALL` |
| Approve / reject / revoke concessions, reverse payments | `FEE_APPROVE` + `FEE_SCOPE_ALL` |

## 4. Code

| Piece | File |
|---|---|
| Entities, enums | `entity/*`, `enums/*` |
| JPA repositories, native SQL (lists, totals, numbers) | `repository/*`, `repository/FeeQueries.java` |
| Arithmetic (split, late fine, concession shares, ₹ formatting, amount in words) | `service/FeeMath.java` |
| Year / actor / settings / access / lock | `service/FeeContext.java` |
| Billing, charges, cancel | `service/FeeInvoiceService.java` |
| Ledgers, defaulters | `service/FeeLedgerService.java` |
| Payments, cheques, reversals, receipts, day book | `service/FeePaymentService.java` |
| Concessions | `service/FeeConcessionService.java` |
| Reminders, guardian messages | `service/FeeReminderService.java`, `service/FeeNotifier.java` |
| Summary, collections report, dashboard figures | `service/FeeReportService.java` |
| HTTP | `controller/FeeController.java` (`/api/v1/fees`), `controller/StudentFeeController.java` |

Tests: `FeeMathTest` (arithmetic and allocation), `FeeCollectionPostgresIntegrationTest` (billing → concession → cash
and cheque → reversal → totals → revoke / cancel / charge → defaulters and reminders, on the real schema).

## 5. Online payments

Mandates, the monthly auto-debit (siblings higher class first, NACH bounce charge + GST), payment links and orders through the
school's own Cashfree / Razorpay account: `online/ONLINE_FEES.md`. They use this module through
`FeePaymentService.recordGatewayPayment` (oldest dues first; `pending` = held like a cheque) / `settleGatewayPayment` and
`FeeInvoiceService.addCharge`; modes `NACH` and `ONLINE`.

## 6. Not built

PDF receipts and Excel / CSV exports, Tally export, advances / credit balances (overpayment is refused), automatic late-fine
charges, evidence uploads for concessions, GSTIN on receipts, gateway refunds.
