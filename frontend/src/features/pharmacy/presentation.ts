import type { StatusTone } from "@/components/ui/StatusBadge";
import type {
  DispenseStatus,
  PrescriptionStatus,
} from "./types";

interface StatusPresentation {
  label: string;
  tone: StatusTone;
}

// `satisfies Record` makes a backend enum addition fail typecheck until it has a UI label.
export const prescriptionStatusPresentation = {
  ACTIVE: {
    label: "Đang hiệu lực",
    tone: "info",
  },
  FULFILLED: {
    label: "Đã hoàn tất",
    tone: "success",
  },
  CANCELLED: {
    label: "Đã hủy",
    tone: "neutral",
  },
  EXPIRED: {
    label: "Hết hiệu lực",
    tone: "warning",
  },
  DISPENSE_FAILED: {
    label: "Xuất thuốc thất bại",
    tone: "danger",
  },
} satisfies Record<PrescriptionStatus, StatusPresentation>;

export const dispenseStatusPresentation = {
  PENDING: {
    label: "Chờ thanh toán / xuất thuốc",
    tone: "warning",
  },
  DISPENSED: {
    label: "Đã xuất thuốc",
    tone: "success",
  },
  FAILED: {
    label: "Xuất thất bại",
    tone: "danger",
  },
  CANCELLED: {
    label: "Đã hủy",
    tone: "neutral",
  },
  EXPIRED: {
    label: "Hết hiệu lực",
    tone: "warning",
  },
} satisfies Record<DispenseStatus, StatusPresentation>;
