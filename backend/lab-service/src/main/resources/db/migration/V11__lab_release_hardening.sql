-- Release copies template TEXT values and "Không có kết quả: <reason>" into lab_result.value.
ALTER TABLE lab_result ALTER COLUMN value TYPE VARCHAR(4000);

-- Everyone who saved a draft of the revision; none of them may release it.
ALTER TABLE lab_report ADD COLUMN authors UUID[] NOT NULL DEFAULT '{}';
UPDATE lab_report SET authors = ARRAY[entered_by] WHERE entered_by IS NOT NULL;
