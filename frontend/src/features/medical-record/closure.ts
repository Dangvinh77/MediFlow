import type { RecordClosureReason } from "./types";

export const closureReasonLabels: Record<RecordClosureReason, string> = {
  PATIENT_LEFT: "Bệnh nhân tự ý về",
  PATIENT_REFUSED_EXAM: "Bệnh nhân từ chối khám",
  PATIENT_REFUSED_ORDER: "Bệnh nhân từ chối chỉ định",
  OTHER: "Lý do khác",
};
