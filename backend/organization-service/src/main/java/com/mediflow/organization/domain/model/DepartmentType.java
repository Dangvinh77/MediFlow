package com.mediflow.organization.domain.model;
/**
 * Loại của khoa/phòng trong bệnh viện.
 *
 * CLINICAL:
 * Khoa lâm sàng, trực tiếp khám/chữa bệnh.
 * Ví dụ: Nội, Ngoại, Nhi...
 *
 * PARACLINICAL:
 * Khoa cận lâm sàng.
 * Ví dụ: Xét nghiệm, Chẩn đoán hình ảnh...
 *
 * ADMINISTRATIVE:
 * Khoa/phòng hành chính.
 * Ví dụ: Phòng Tài chính, Nhân sự...
 */
public enum DepartmentType {

    CLINICAL,

    PARACLINICAL,

    ADMINISTRATIVE
}