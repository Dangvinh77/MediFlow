-- Demo catalogue for MediFlow v2.0. Ranges are illustrative and marked reviewed = false:
-- a real facility replaces them with values its laboratory has approved.
INSERT INTO lab_result_template (template_code, version, name, active) VALUES
    ('CBC', 1, 'Công thức máu', true),
    ('GLU', 1, 'Glucose máu', true),
    ('HBSAG', 1, 'HBsAg', true),
    ('UA', 1, 'Tổng phân tích nước tiểu', true),
    ('MICRO-STAIN', 1, 'Soi nhuộm vi sinh', true);

INSERT INTO lab_template_field (template_code, version, field_code, label, position, required, value_type, unit,
                                allowed_options, allows_operator) VALUES
    ('CBC', 1, 'WBC', 'Bạch cầu (WBC)', 1, true, 'QUANTITATIVE', 'G/L', NULL, false),
    ('CBC', 1, 'RBC', 'Hồng cầu (RBC)', 2, true, 'QUANTITATIVE', 'T/L', NULL, false),
    ('CBC', 1, 'HGB', 'Huyết sắc tố (HGB)', 3, true, 'QUANTITATIVE', 'g/L', NULL, false),
    ('CBC', 1, 'HCT', 'Hematocrit (HCT)', 4, true, 'QUANTITATIVE', 'L/L', NULL, false),
    ('CBC', 1, 'PLT', 'Tiểu cầu (PLT)', 5, true, 'QUANTITATIVE', 'G/L', NULL, false),
    ('GLU', 1, 'GLU', 'Glucose', 1, true, 'QUANTITATIVE', 'mmol/L', NULL, true),
    ('HBSAG', 1, 'HBSAG', 'HBsAg', 1, true, 'QUALITATIVE', NULL,
        ARRAY['Âm tính', 'Dương tính', 'Chưa xác định'], false),
    ('UA', 1, 'SG', 'Tỷ trọng', 1, true, 'QUANTITATIVE', NULL, NULL, false),
    ('UA', 1, 'PH', 'pH', 2, true, 'QUANTITATIVE', NULL, NULL, false),
    ('UA', 1, 'PRO', 'Protein', 3, true, 'SEMI_QUANTITATIVE', NULL, ARRAY['-', '+', '++', '+++'], false),
    ('UA', 1, 'GLU', 'Glucose niệu', 4, true, 'SEMI_QUANTITATIVE', NULL, ARRAY['-', '+', '++', '+++'], false),
    ('UA', 1, 'NIT', 'Nitrit', 5, false, 'QUALITATIVE', NULL, ARRAY['Âm tính', 'Dương tính'], false),
    ('MICRO-STAIN', 1, 'STAIN', 'Kết quả soi nhuộm', 1, true, 'TEXT', NULL, NULL, false),
    ('MICRO-STAIN', 1, 'ORGANISM', 'Tác nhân phát hiện', 2, false, 'TEXT', NULL, NULL, false);

INSERT INTO lab_reference_range (range_id, template_code, version, field_code, position, sex, age_min_days,
                                 age_max_days, low, high, critical_low, critical_high, reviewed) VALUES
    (gen_random_uuid(), 'CBC', 1, 'WBC', 1, NULL, 6570, NULL, 4, 10, 2, 30, false),
    (gen_random_uuid(), 'CBC', 1, 'RBC', 1, 'MALE', 6570, NULL, 4.2, 5.4, NULL, NULL, false),
    (gen_random_uuid(), 'CBC', 1, 'RBC', 2, 'FEMALE', 6570, NULL, 3.8, 5.0, NULL, NULL, false),
    (gen_random_uuid(), 'CBC', 1, 'HGB', 1, 'MALE', 6570, NULL, 130, 170, 70, 200, false),
    (gen_random_uuid(), 'CBC', 1, 'HGB', 2, 'FEMALE', 6570, NULL, 120, 150, 70, 200, false),
    (gen_random_uuid(), 'CBC', 1, 'HCT', 1, 'MALE', 6570, NULL, 0.40, 0.50, NULL, NULL, false),
    (gen_random_uuid(), 'CBC', 1, 'HCT', 2, 'FEMALE', 6570, NULL, 0.36, 0.46, NULL, NULL, false),
    (gen_random_uuid(), 'CBC', 1, 'PLT', 1, NULL, 6570, NULL, 150, 400, 20, 1000, false),
    (gen_random_uuid(), 'GLU', 1, 'GLU', 1, NULL, 6570, NULL, 3.9, 6.4, 2.5, 25, false),
    (gen_random_uuid(), 'UA', 1, 'SG', 1, NULL, NULL, NULL, 1.005, 1.030, NULL, NULL, false),
    (gen_random_uuid(), 'UA', 1, 'PH', 1, NULL, NULL, NULL, 4.5, 8.0, NULL, NULL, false);

INSERT INTO lab_service_catalog (service_code, name, display_group, specimen_type, preparation, turnaround_hours,
                                 price_code, template_code, panel, active) VALUES
    ('CBC', 'Công thức máu', 'HEMATOLOGY', 'WHOLE_BLOOD_EDTA', NULL, 4, 'LAB-CBC', 'CBC', true, true),
    ('GLU', 'Glucose máu', 'BIOCHEMISTRY', 'SERUM', 'Nhịn ăn ít nhất 8 giờ nếu bác sĩ yêu cầu', 4,
        'LAB-GLU', 'GLU', false, true),
    ('HBSAG', 'HBsAg', 'IMMUNOLOGY', 'SERUM', NULL, 24, 'LAB-HBSAG', 'HBSAG', false, true),
    ('UA', 'Tổng phân tích nước tiểu', 'URINALYSIS', 'URINE', 'Lấy nước tiểu giữa dòng', 4,
        'LAB-UA', 'UA', true, true),
    ('MICRO-STAIN', 'Soi nhuộm vi sinh', 'MICROBIOLOGY', 'SWAB', NULL, 24, 'LAB-MICRO', 'MICRO-STAIN', false, true);
