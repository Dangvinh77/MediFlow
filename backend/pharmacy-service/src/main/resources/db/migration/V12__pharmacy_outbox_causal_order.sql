-- Preserve prescription event causality across retries and multiple dispatcher replicas.
ALTER TABLE PHARMACY_EVENT_OUTBOX
    ADD COLUMN aggregate_id UUID;

-- All payloads written by pharmacy-service are JSON. Rows from older versions are enriched when
-- their aggregate identifier is present; unidentifiable legacy rows remain globally ordered.
UPDATE PHARMACY_EVENT_OUTBOX
SET aggregate_id = CASE
    WHEN routing_key IN ('stock.low', 'stock.adjusted')
        THEN NULLIF(payload::jsonb ->> 'drugId', '')::UUID
    ELSE NULLIF(payload::jsonb ->> 'prescriptionId', '')::UUID
END
WHERE aggregate_id IS NULL;

CREATE INDEX idx_pharmacy_outbox_aggregate_order
    ON PHARMACY_EVENT_OUTBOX (aggregate_id, created_at, event_id)
    WHERE published_at IS NULL AND quarantined_at IS NULL;
