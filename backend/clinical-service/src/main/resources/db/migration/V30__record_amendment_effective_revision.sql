-- Accepted amendments get a per-record order so same-timestamp corrections have deterministic current content.
ALTER TABLE record_amendment ADD COLUMN effective_revision BIGINT;

WITH accepted AS (
    SELECT amendment_id,
           row_number() OVER (
               PARTITION BY record_id ORDER BY decided_at, requested_at, amendment_id
           ) AS revision
    FROM record_amendment
    WHERE status IN ('APPROVED', 'APPLIED')
)
UPDATE record_amendment a
SET effective_revision = accepted.revision
FROM accepted
WHERE a.amendment_id = accepted.amendment_id;

ALTER TABLE record_amendment ADD CONSTRAINT ck_record_amendment_effective_revision CHECK (
    (status IN ('APPROVED', 'APPLIED') AND effective_revision IS NOT NULL AND effective_revision > 0)
    OR (status IN ('PENDING', 'REJECTED') AND effective_revision IS NULL)
);
