-- Optional prescriber note per line; existing lines keep NULL and are never backfilled.
ALTER TABLE prescription_line ADD COLUMN note VARCHAR(500);
