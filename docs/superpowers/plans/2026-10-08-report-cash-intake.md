# Huy — close the Report gross-receipt runtime gap (2026-10-08)

Scope: Report production and related documentation only. Preserve existing Surgery/Pharmacy work;
no Gateway, other-service, root/Common/Compose/CI edit, automatic commit/push or held-row release.
Existing backlog edges: R-03.1/R-03.2/R-03.7, R-01.5.3.CASH and X-01.2/X-01.3.
These are local implementation checkpoints, not ten new global business tasks.

## Completed implementation checkpoints

- [x] Dedicated durable `report.cash-receipts-v2.q` and original-byte DLQ; exactly `payment.completed`.
- [x] Independent `care-finance-v2 AND report.cash-receipt-consumer.enabled` gates, defaults false;
  absent/partial gate combinations create no topology, consumer or listener factory.
- [x] Thin consumer uses the strict wire port and existing cash in-port; no finance logic at the broker edge.
- [x] ACK only after the receipt application's transaction returns; source/delivery and both scopes atomic.
- [x] Permanent malformed/conflicting input rejects without a sensitive cause. Storage failure has three
  attempts then dedicated DLQ; PostgreSQL sequence proves all three attempts rolled back their effects.
- [x] Read the actual two Billing fixtures, without changing producer bytes or inventing revisions.
- [x] Duplicate delivery/new event ID count once; distinct transactions on one invoice count independently;
  reused event ID with a different transaction rolls back the second source row.
- [x] SERVICE_PAYMENT and ADMISSION_DEPOSIT stay separate; exact large-cent decimals do not use double;
  wrong IDs/amount/version/producer/classification/business time reject before durable claims.
- [x] Real listener stop/start and retained-byte SQL-failure recovery; accepted facts feed existing finite
  cash replay and reach VERIFIED. Listener restart is not a separate JVM crash or complete live cutover.
- [x] Concurrent compatibility and versioned listeners: deposit reaches only gross receipts, legacy DLQ
  retains the same V1 bytes, legacy revenue/claims and full financial/publication tables stay unchanged.

## Verification

Focused Maven exit 0: **32 tests, zero failure/error/skip** — 6 consumer, 6 configuration and
20 real PostgreSQL/RabbitMQ tests. First run failed in test cleanup because the fixture referred to
`report_cash_replay_generation` instead of actual V13 `cash_replay_generation`; only the test setup
was corrected, no migration or production behavior was changed to accommodate it.

Full clean Report regression: **490 tests / 60 fresh reports, zero failure/error/skip**, Maven
exit 0, 09:58:57–10:03:11 Asia/Bangkok. This includes all 32 additions and the existing operational,
cash replay, telemetry, legacy, security and architecture cases. Focused reruns are not added twice.
No migration or Billing fixture changed. Broker teardown reconnect messages were logged after
test-owned containers stopped; no test failed or was skipped.

```powershell
# Sequential reactors: backend/common/target is shared.
mvn -q -pl backend/report-service -am '-Dtest=CashReceiptConsumerTest,ReportCashReceiptConsumerConfigurationTest,CashReceiptRabbitIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
mvn -q -pl backend/report-service -am clean '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
```

### Exact producer-byte manifest

Producer: Billing, `payment.completed` V1, actual singleton PAYMENT transaction. Consumer:
Report `CashReceiptConsumer` → `ApplyCashReceiptUseCase` → V12 source/delivery/gross scopes,
then V13 finite replay. SHA-256 is of the actual fixture file, not reconstructed JSON.

| Billing producer fixture | SHA-256 |
|---|---|
| [service receipt](../../../backend/billing-service/src/test/resources/contracts/ledger-v1/payment-service.json) | `000ed3cf78df31a63793e1fb2be542ff11995a97051febfcffaaa3db0bb65a7e` |
| [deposit receipt](../../../backend/billing-service/src/test/resources/contracts/ledger-v1/payment-deposit.json) | `afb2b0e640db9c95fb82cf856bcc72802bd2a0cdf128c3735c9c6279c4e1e550` |

Consumer mutation tests are adversarial scenarios, not extra approved producer fixtures.

## Operational boundary and remaining release gates

`MEDIFLOW_REPORT_CASH_RECEIPT_CONSUMER_ENABLED=false` remains the deployment default.
Intake disablement stops future consumption; it does not erase receipts/journal or retract a prior
committed effect. Do not truncate user data, clear dedupe rows or automatically drain a production DLQ.
Rollout still requires source coverage, held-producer publication approval and coordinated retention.

This completes the missing cash-only broker adapter, not five-metric finance. Gross inflow is before
refunds, not earned revenue, net cash or current deposit liability. Allocation/release/recognition,
refund linkage and settlement revision/equations still need actual canonical source inputs. No
financial HTTP API/read publication, history backfill, currency conversion or producer release is
introduced. Surgery referral/clinical/legal policy and Pharmacy prescribing/placement authority
are still required for their public workflows, as recorded in the existing active handoffs.

The user subsequently granted task-scoped production edits to Clinical, Inpatient, Billing and
Notification in this turn to implement remaining source/consumer dependencies. Gateway remains
read-only, as do root/Common/Compose/CI. The cash intake changes themselves affect Report and docs
only; later cross-service execution must record its separate verification, not reuse this result.

Main backlog/fixed selection counts are not reduced by turning runtime checkpoints into full
contract tasks. See [main plan](2026-09-25-huy-surgery-pharmacy-report.md) and
[previous full-module evidence](2026-10-08-huy-ready-task-completion.md).
