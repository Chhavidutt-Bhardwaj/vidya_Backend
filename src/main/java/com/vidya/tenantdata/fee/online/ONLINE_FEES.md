# Online fees (`tenantdata/fee/online`)

Parents pay through **the school's own** Cashfree or Razorpay merchant account: eNACH / UPI Autopay mandates with a monthly
auto-debit, payment links, and checkout orders. Built on the structure of infi_payment (mandate → on-demand charge → webhook;
links; orders; bounce + GST; part payments), on top of fee collection (`../FEES.md`). API: `APiReqDoc/06_FEES_FINANCE.md` §5.
Tables: tenant `V30__online_fees_mandates_autodebit.sql`. No new permissions (`FEE_PAY` for parents existed).

## 1. Model

| Table | What |
|---|---|
| `fee_gateway_account` | The school's keys per gateway; secrets AES-GCM encrypted (`SecretCipher`, key `vidya.fees.online.secret-key`) |
| `fee_autodebit_settings` | One row: on/off, gateways, debit day (10), notice days (3,1), invite lead (7), sibling gap (1 day), attempts (2), retry gap (3), bounce ₹500 + GST 18 %, mandate max, link expiry |
| `fee_mandate` + `fee_mandate_student` | A payer's mandate and the children it covers; `live` + partial unique index = a child on at most one pending / active mandate |
| `fee_debit` | One per mandate × child × month × attempt (unique); `sibling_rank`, `scheduled_on`, amount, status, bounce charge / GST, the `fee_payment` holding the money |
| `fee_debit_run` | Each run of the job and its counts |
| `fee_checkout` + `fee_checkout_student` | A link / order for one or more children, rank 0 = highest class; paid / unallocated |
| `fee_gateway_payment` | Each gateway payment applied once (unique provider + id) |
| `fee_gateway_event` | Raw webhooks and the outcome (APPLIED / IGNORED / DUPLICATE / REJECTED / ERROR) |

`fee_payment.mode` gains `NACH` (auto-debit) and `ONLINE` (link / order); the counter refuses both (`FEE_MODE_ONLINE_ONLY`).

## 2. Rules

**Payer.** A parent has one `student_guardian` row per child; they are one payer by login (`guardian_user_id`), else by the last
ten phone digits (`PayerAccess.payerRows`). Office (`FEE_MANAGE` + school scope) acts for anyone; a parent (`FEE_PAY`) only as
themselves for their own children.

**Mandate** (`MandateService`). Created `PENDING` with a reference `MD…` (= Cashfree `subscription_id` / Razorpay auth-link receipt).
Cashfree: ON_DEMAND subscription, ₹1 authorisation (refunded), Cashfree sends the link. Razorpay: registration link (`emandate`, or
`upi` with ₹1), link sent as `FEE_MANDATE_REQUEST`; `invoice.paid` gives the token, `token.confirmed` activates. Webhook / poll
updates go through `transition`: an ended mandate never comes back. Ending releases the children and cancels debits not yet sent.
`syncPending` (hourly) polls pending mandates quiet for an hour and expires mandates past `expires_on`.

**Monthly debit** (`AutoDebitService.run` → `AutoDebitSteps`; daily 07:30, or `POST /debits/run`):
1. *Plan* when the debit day is at most `max(notice days) + 1` ahead (or `planMonth` catch-up): for each `ACTIVE` mandate activated
   before the debit day, its children **higher class first** (`academic_class.sort_order` desc); each child with something due by
   its date gets a debit on `debit day + rank × gap` (never earlier than tomorrow). Only missing attempts are added.
2. *Invite* guardians of children with dues and no mandate, once per month and person (`FEE_MANDATE_INVITE`, dedupe per debit day).
3. *Notice* each scheduled debit `notice days` before (or the day it is planned late): `FEE_AUTO_DEBIT_NOTICE` to the mandate's
   guardian row only (`FeeNotifier.Message.onlyGuardianIds`).
4. *Submit* when `scheduled_on − gateway lead ≤ today` (Cashfree 1 day: its payment is scheduled for the debit day; Razorpay 0).
   `prepare` (one transaction, ledger locked) re-reads the due by that day — 0 → `SKIPPED` — and records a `PENDING_CLEARANCE`
   `NACH` payment on those lines; then the gateway call runs **outside** any transaction; `submitted` / `submitFailed` record the answer.
   A crash between them leaves `SUBMITTING`; the webhook still settles it (matched by reference / order id / payment id).
5. *Outcome* (webhook): success → `settleGatewayPayment(true)` (paid, realized today), receipt. Failure → hold released (`BOUNCED`),
   two charge lines due today on the ledger — heads `NACH_BOUNCE` and `NACH_BOUNCE_GST` (created on first use, optional, category
   OTHER): base and `round(base × GST % / 100)` — then a retry `retry_after_days` later while `attempt < max_attempts` and the mandate
   is active (its amount includes the charges), and `FEE_AUTO_DEBIT_FAILED`. A gateway refusal at submission is `ERROR`, no charge.

**Links and orders** (`CheckoutService`). Amount ≤ what is due on the chosen children (default all of it). Payments come by webhook:
Cashfree links report the running total (delta applied), Razorpay each payment. `split`: the money goes to the children in rank order,
each up to its current due; within a child, oldest dues first (`FeePaymentService.recordGatewayPayment`). One `ONLINE` receipt per
child; `PARTIALLY_PAID` until the whole amount is in; anything left over is `unallocated_paise` (refund).

**Webhooks** (`FeeWebhookController` → `FeeWebhookService`): `/api/v1/webhooks/fees/{tenantCode}/{provider}` is open; the tenant is
set from the path (must be accessible), the signature checked with that school's keys, each event applied in its own transaction:
`MandateUpdate` → mandate; `PaymentReport` → debit, else checkout order; `LinkReport` → link. Answers: 200, 401 bad signature, 404
unknown school / gateway not connected, 503 handler error (the gateway retries). Idempotency: event id (Razorpay), unique gateway
payment ids, and status checks under row locks.

## 3. Code

| Piece | File |
|---|---|
| Gateway contract and models | `gateway/FeeGateway.java`, `gateway/GatewayModels.java` |
| Cashfree / Razorpay (REST + JSON over JDK `HttpClient`, no SDKs) | `gateway/CashfreeFeeGateway.java`, `gateway/RazorpayFeeGateway.java` |
| Secret encryption | `gateway/SecretCipher.java` |
| Settings, gateway accounts, credentials | `service/OnlineFeeConfigService.java` |
| Payer resolution and access | `service/PayerAccess.java` |
| Mandates | `service/MandateService.java` |
| Monthly debit | `service/AutoDebitService.java`, `service/AutoDebitSteps.java`, `service/OnlineFeeScheduler.java` |
| Links / orders, sibling split | `service/CheckoutService.java` |
| Webhooks | `service/FeeWebhookService.java`, `service/WebhookLog.java`, `controller/FeeWebhookController.java` |
| HTTP | `controller/OnlineFeeController.java` (`/api/v1/fees/online`) |

Config (`application.yml`, `vidya.fees.online`): `secret-key` (`VIDYA_FEES_GATEWAY_SECRET_KEY`), `webhook-base-url`
(`VIDYA_PUBLIC_API_URL`), `scheduler.enabled`, `autodebit-cron`, `mandate-sync-cron`.

Tests: `OnlineFeeUnitTest` (request bodies, webhook signatures and parsing of both gateways, sibling split, GST, encryption, webhook
routing), `OnlineFeesPostgresIntegrationTest` (mandate → plan with sibling order → notice / invite → submit → bounce + GST + retry →
paid-before skip → retry succeeds → sibling part payment on a link → cancel).

## 4. Not built

Refunds through the gateway, gateway settlement reports, polling a missed link / order payment (webhooks only), presentation of a
mandate's bank account details, PayU / Easebuzz (add a `FeeGateway`).
