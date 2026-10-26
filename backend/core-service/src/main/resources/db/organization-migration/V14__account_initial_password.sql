-- Persist the initial-password restriction independently of browser state.
ALTER TABLE account ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
