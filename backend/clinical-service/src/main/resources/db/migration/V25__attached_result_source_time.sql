-- When the source produced the result. A result Lab reported before the visit ended may arrive just after it,
-- so documents compare this time, not the arrival time, with the end of the visit. Older rows keep created_at.
ALTER TABLE attached_result ADD COLUMN source_at TIMESTAMPTZ;
