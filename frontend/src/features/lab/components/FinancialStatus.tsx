import { StatusBadge } from "@/components/ui/StatusBadge";
import type { LabTestDTO } from "../types";
export function FinancialStatus({ test }: { test: LabTestDTO }) {
  if (test.careContractVersion === 1) return <StatusBadge tone={test.clearanceId ? "info" : "warning"}>{test.clearanceId ? "Có xác nhận tài chính" : "Chưa có xác nhận tài chính"}</StatusBadge>;
  return <StatusBadge tone={test.paid ? "success" : "warning"}>{test.paid ? "Đã thanh toán" : "Chưa thanh toán"}</StatusBadge>;
}
