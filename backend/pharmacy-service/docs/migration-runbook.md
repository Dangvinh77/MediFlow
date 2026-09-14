# Pharmacy database migration runbook

## Scope

Flyway migrations in `src/main/resources/db/migration` are forward-only and are applied in
version order. Released migration files must never be edited or renamed; add a new version for
every schema change.

## Before applying a migration

1. Take a PostgreSQL backup of the pharmacy database and verify that it can be restored.
2. Confirm the target release contains the migration files and that `ddl-auto` is `validate`.
3. Run the fresh-database and V4-upgrade scenarios from
   `PharmacyMigrationCompatibilityTest` in CI with Docker enabled.
4. Record the Flyway schema history version and the backup identifier in the deployment log.

## Applying and verifying

Run the pharmacy service once against the target database, then verify Flyway reports the latest
version and Hibernate validation succeeds. For V11, verify `PHARMACY_SCHEDULER_LEASE.lease_token`
is populated for existing rows and that a second scheduler cannot claim a live lease.

Legacy payment rows intentionally remain without a `PAYMENT_RECEIPT`; operators must not infer a
successful payment from an old prescription or backfill a receipt without the Billing contract.

## Failure and rollback

Migrations are not rolled back in place. If deployment verification fails, stop the application,
restore the pre-migration backup into a replacement database, and redeploy the previously known
good service version. Investigate and ship a corrective forward migration after the restored
database is healthy.
