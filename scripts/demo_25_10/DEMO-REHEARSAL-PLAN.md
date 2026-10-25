# Demo rehearsal and combined PR

The user authorized completing these follow-ups before creating one PR. Base:
`64a1f04340efe07d324a1335d44c2b90baac68fd`, branch `codex/fix-lab-clearance-identity`.

- [x] Add Billing consumer regression tests using the actual Clinical/Lab V1 fixtures;
  run `mvn -q -pl backend/billing-service -am -Dtest=BillingEventConsumerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  before production changes. V1 lifecycle facts must not create a second legacy fee.
- [x] Correct V1 dispatch with strict contract validation and preserve V0 compatibility;
  run targeted tests and `mvn -q -pl backend/billing-service -am test`.
- [x] Rebuild packaged applications and run the Lab suite plus separate packaged acceptance against
  real isolated PostgreSQL/RabbitMQ, retaining evidence of the fresh paid normal flow.
- [x] Restore all eight snapshot databases to a new isolated PostgreSQL container;
  verify restore guards and start all six business deployments with Gateway/Eureka.
- [x] Run frontend typecheck, lint, tests/build and mobile checks/build; exercise
  available browser/mobile surfaces against the rehearsed Gateway. Record toolchain
  or runtime blockers without promoting partial checks to full acceptance.
- [x] Correct demo startup instructions, record commands/results and remaining limits,
  request code review and resolve findings.
- Publication: commit this verified change and open one combined PR. Do not merge.

Production scope: Lab and Billing. Demo scripts/docs are explicitly assigned shared
work. Preserve historical snapshot audit rows and existing local work; no SQL edits
to clearance identities, state, or inbox/outbox delivery status.
