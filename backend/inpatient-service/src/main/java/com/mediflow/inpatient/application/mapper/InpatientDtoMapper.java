package com.mediflow.inpatient.application.mapper;

import com.mediflow.inpatient.application.dto.response.AdmissionDTO;
import com.mediflow.inpatient.application.dto.response.BedDTO;
import com.mediflow.inpatient.application.dto.response.ClinicalOrderReferenceDTO;
import com.mediflow.inpatient.application.dto.response.TreatmentEntryDTO;
import com.mediflow.inpatient.domain.model.Admission;
import com.mediflow.inpatient.domain.model.Bed;
import com.mediflow.inpatient.domain.model.ClinicalOrderReference;
import com.mediflow.inpatient.domain.model.TreatmentEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface InpatientDtoMapper {

    @Mapping(target = "maDotNoiTru", expression = "java(admission.admissionId())")
    @Mapping(target = "maYeuCauNoiTru", expression = "java(admission.admissionRequestId())")
    @Mapping(target = "maBenhNhan", expression = "java(admission.patientId())")
    @Mapping(target = "maHoSoNguon", expression = "java(admission.sourceRecordId())")
    @Mapping(target = "maKhoa", expression = "java(admission.departmentId())")
    @Mapping(target = "doUuTien", expression = "java(admission.priority())")
    @Mapping(target = "capCuu", expression = "java(admission.emergency())")
    @Mapping(target = "status", expression = "java(admission.status())")
    @Mapping(target = "maGiuongDangSuDung", source = "activeBedId")
    @Mapping(target = "maXacNhanTamUng", expression = "java(admission.depositClearanceId())")
    @Mapping(target = "maQuyetToan", expression = "java(admission.settlementId())")
    @Mapping(target = "maTomTatRaVien", expression = "java(admission.dischargeSummaryId())")
    @Mapping(target = "maPheDuyetNgoaiLe", expression = "java(admission.emergencyOverrideId())")
    @Mapping(target = "thoiGianYeuCau", expression = "java(admission.requestedAt())")
    @Mapping(target = "thoiGianNhapVien", expression = "java(admission.admittedAt())")
    @Mapping(target = "thoiGianRaVienYTe", expression = "java(admission.medicallyDischargedAt())")
    @Mapping(target = "thoiGianDong", expression = "java(admission.closedAt())")
    @Mapping(target = "thoiGianHuy", expression = "java(admission.cancelledAt())")
    @Mapping(target = "lyDoHuy", expression = "java(admission.cancellationReason())")
    @Mapping(target = "yeuLenhNgoai", source = "references")
    AdmissionDTO toAdmissionDto(Admission admission, UUID activeBedId,
                                List<ClinicalOrderReference> references);

    @Mapping(target = "maThamChieu", expression = "java(reference.referenceId())")
    @Mapping(target = "maDotNoiTru", expression = "java(reference.admissionId())")
    @Mapping(target = "loaiYLenh", expression = "java(reference.orderType())")
    @Mapping(target = "maYLenhBenNgoai", expression = "java(reference.externalOrderId())")
    @Mapping(target = "status", expression = "java(reference.status())")
    @Mapping(target = "tomTat", expression = "java(reference.summary())")
    @Mapping(target = "phienBanSuKien", expression = "java(reference.eventVersion())")
    ClinicalOrderReferenceDTO toOrderDto(ClinicalOrderReference reference);

    @Mapping(target = "maGiuong", expression = "java(bed.bedId())")
    @Mapping(target = "maKhoa", expression = "java(bed.departmentId())")
    @Mapping(target = "maKhu", expression = "java(bed.wardCode())")
    @Mapping(target = "maPhong", expression = "java(bed.roomCode())")
    @Mapping(target = "maGiuongTrongPhong", expression = "java(bed.bedCode())")
    @Mapping(target = "loaiGiuong", expression = "java(bed.bedType())")
    @Mapping(target = "status", expression = "java(bed.status())")
    @Mapping(target = "active", expression = "java(bed.active())")
    BedDTO toBedDto(Bed bed);

    @Mapping(target = "maMucDienBien", expression = "java(entry.entryId())")
    @Mapping(target = "maDotNoiTru", expression = "java(entry.admissionId())")
    @Mapping(target = "loaiMuc", expression = "java(entry.entryType())")
    @Mapping(target = "noiDung", expression = "java(entry.content())")
    @Mapping(target = "nguoiGhi", expression = "java(entry.authoredBy())")
    @Mapping(target = "thoiGianGhi", expression = "java(entry.recordedAt())")
    @Mapping(target = "maMucBiDinhChinh", expression = "java(entry.correctionOfEntryId())")
    TreatmentEntryDTO toTreatmentDto(TreatmentEntry entry);
}
