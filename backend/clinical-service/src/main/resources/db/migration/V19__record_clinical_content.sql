-- Structured examination content per visit. Each section is optional free text; null means not written.
ALTER TABLE medical_record
    ADD COLUMN chief_complaint TEXT CHECK (length(chief_complaint) <= 4000),
    ADD COLUMN present_illness TEXT CHECK (length(present_illness) <= 4000),
    ADD COLUMN past_history    TEXT CHECK (length(past_history) <= 4000),
    ADD COLUMN allergy_note    TEXT CHECK (length(allergy_note) <= 4000),
    ADD COLUMN physical_exam   TEXT CHECK (length(physical_exam) <= 4000),
    ADD COLUMN advice          TEXT CHECK (length(advice) <= 4000);
