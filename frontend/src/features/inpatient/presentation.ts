import type { StatusTone } from "@/components/ui/StatusBadge";
import type {
  AdmissionPriority,
  AdmissionStatus,
  BedStatus,
  ClinicalOrderType,
  ExternalOrderStatus,
} from "./types";

type Presentation = { label: string; tone: StatusTone };

export const admissionStatusPresentation: Record<AdmissionStatus, Presentation> = {
  REQUESTED: { label: "Đã yêu cầu", tone: "info" },
  AWAITING_BED: { label: "Chờ giường", tone: "warning" },
  AWAITING_DEPOSIT: { label: "Chờ tạm ứng", tone: "warning" },
  READY: { label: "Sẵn sàng nhập viện", tone: "info" },
  ADMITTED: { label: "Đang điều trị", tone: "success" },
  MEDICALLY_DISCHARGED: { label: "Đã ra viện y tế", tone: "info" },
  CLOSED: { label: "Đã quyết toán", tone: "neutral" },
  CANCELLED: { label: "Đã hủy", tone: "danger" },
};

export const admissionPriorityLabel: Record<AdmissionPriority, string> = {
  ROUTINE: "Thường",
  URGENT: "Khẩn",
  EMERGENCY: "Cấp cứu",
};

export const bedStatusPresentation: Record<BedStatus, Presentation> = {
  AVAILABLE: { label: "Còn trống", tone: "success" },
  OCCUPIED: { label: "Đang sử dụng", tone: "warning" },
  OUT_OF_SERVICE: { label: "Ngưng phục vụ", tone: "neutral" },
};

export const clinicalOrderTypeLabel: Record<ClinicalOrderType, string> = {
  LAB_TEST: "Xét nghiệm",
  PRESCRIPTION: "Đơn thuốc",
  SURGERY: "Phẫu thuật",
};

export const externalOrderStatusPresentation: Record<ExternalOrderStatus, Presentation> = {
  REQUESTED: { label: "Đã yêu cầu", tone: "info" },
  READY: { label: "Sẵn sàng", tone: "info" },
  IN_PROGRESS: { label: "Đang thực hiện", tone: "warning" },
  COMPLETED: { label: "Hoàn tất", tone: "success" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
  FAILED: { label: "Thất bại", tone: "danger" },
};
