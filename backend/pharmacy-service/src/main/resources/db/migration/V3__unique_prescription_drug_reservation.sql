ALTER TABLE STOCK_RESERVATION
    ADD CONSTRAINT uk_reservation_prescription_drug
    UNIQUE (prescription_id, drug_id);