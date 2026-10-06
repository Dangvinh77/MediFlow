-- Owned projections only; no clinical, room or payment reference data is seeded.
CREATE INDEX idx_surgery_case_department_requested
    ON surgery_case(department_id, requested_at DESC, surgery_case_id);
CREATE INDEX idx_surgery_case_status_requested
    ON surgery_case(status, requested_at DESC, surgery_case_id);
CREATE INDEX idx_surgery_inbox_due_by_type
    ON surgery_inbox(event_type, next_attempt_at, received_at, event_id)
    WHERE status = 'PENDING' AND next_attempt_at IS NOT NULL;
