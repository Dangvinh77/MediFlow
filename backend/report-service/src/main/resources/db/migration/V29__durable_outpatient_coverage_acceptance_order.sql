-- Preserve the old accepted_at/coverage_id selection for existing rows, then assign every
-- subsequent serialized approval a strictly increasing database order. This adds metadata only.
CREATE SEQUENCE report_outpatient_coverage_acceptance_order_seq;

ALTER TABLE report_outpatient_coverage
    ADD COLUMN acceptance_order BIGINT;

WITH ranked AS (
    SELECT coverage_id,
           ROW_NUMBER() OVER (ORDER BY accepted_at ASC, coverage_id DESC) AS acceptance_order
    FROM report_outpatient_coverage
)
UPDATE report_outpatient_coverage AS coverage
SET acceptance_order = ranked.acceptance_order
FROM ranked
WHERE ranked.coverage_id = coverage.coverage_id;

SELECT setval(
    'report_outpatient_coverage_acceptance_order_seq',
    COALESCE(MAX(acceptance_order), 1),
    MAX(acceptance_order) IS NOT NULL
)
FROM report_outpatient_coverage;

ALTER TABLE report_outpatient_coverage
    ALTER COLUMN acceptance_order SET NOT NULL,
    ALTER COLUMN acceptance_order SET DEFAULT nextval('report_outpatient_coverage_acceptance_order_seq'),
    ADD CONSTRAINT report_outpatient_coverage_acceptance_order_positive CHECK (acceptance_order > 0),
    ADD CONSTRAINT report_outpatient_coverage_acceptance_order_unique UNIQUE (acceptance_order);

ALTER SEQUENCE report_outpatient_coverage_acceptance_order_seq
    OWNED BY report_outpatient_coverage.acceptance_order;
