import '../domain/prescription.dart';

String prescriptionStatusText(PrescriptionStatus status) => switch (status) {
  PrescriptionStatus.active => 'Có thể sử dụng',
  PrescriptionStatus.fulfilled => 'Đã hoàn tất',
  PrescriptionStatus.cancelled => 'Đã hủy',
  PrescriptionStatus.expired => 'Hết hạn',
  PrescriptionStatus.dispenseFailed => 'Xuất thuốc thất bại',
};
