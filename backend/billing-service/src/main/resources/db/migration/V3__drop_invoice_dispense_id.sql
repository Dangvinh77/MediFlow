-- Bỏ cột INVOICE.dispense_id: chốt với pharmacy (2026-09-16) rằng prescription.filled
-- không mang dispenseId, nên billing không còn gán/lưu trường này (xem
-- THELOC-INTEGRATION-FOLLOWUP.md mục "prescription.filled — dispenseId").
-- Cột chưa từng được production code gán giá trị (luôn NULL) — an toàn để xoá thẳng,
-- không cần backfill.
ALTER TABLE INVOICE DROP COLUMN dispense_id;
