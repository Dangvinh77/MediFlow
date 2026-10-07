package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tiep_nhan_su_kien_phau_thuat")
public class SurgeryEventReceiptJpaEntity {
    @Id @Column(name = "receipt_id", nullable = false) public UUID maTiepNhan;
    @Column(name = "event_id", nullable = false, unique = true) public UUID maSuKien;
    @Column(name = "event_type", nullable = false, length = 64) public String loaiSuKien;
    @Column(name = "operation_id", nullable = false) public UUID maNghiepVu;
    @Column(name = "payload_fingerprint", nullable = false, length = 64) public String dauVanTai;
    @Column(name = "surgery_case_id", nullable = false) public UUID maCaMo;
    @Column(name = "surgery_request_id", nullable = false) public UUID maYeuCauMo;
    @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Column(name = "patient_id", nullable = false) public UUID maBenhNhan;
    @Column(name = "department_id", nullable = false) public UUID maKhoa;
    @Column(name = "case_revision", nullable = false) public int phienBanCa;
    @Column(name = "source_revision") public Integer phienBanNguon;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_status", nullable = false, length = 20)
    public ExternalOrderStatus trangThaiDich;
    @Column(name = "summary") public String tomTat;
    @Column(name = "timeline_content") public String noiDungDienBien;
    @Column(name = "timeline_at") public Instant thoiGianDienBien;
    @Column(name = "received_at", nullable = false) public Instant nhanLuc;
    @Column(name = "applied_at") public Instant apDungLuc;
    public SurgeryEventReceiptJpaEntity() { }
}
