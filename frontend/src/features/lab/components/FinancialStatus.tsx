
import { L10nValue } from "@/components/i18n/LocaleProvider";
import { StatusBadge } from "@/components/ui/StatusBadge";
import type { LabTestDTO } from "../types";
export function FinancialStatus({ test }: { test: LabTestDTO }) {
  if (test.careContractVersion === 1) return <StatusBadge tone={test.clearanceId ? "info" : "warning"}><L10nValue>{test.clearanceId ? "Có xác nhận tài chính" : "Chưa có xác nhận tài chính"}</L10nValue></StatusBadge>;
  return <StatusBadge tone={test.paid ? "success" : "warning"}><L10nValue>{test.paid ? "Đã thanh toán" : "Chưa thanh toán"}</L10nValue></StatusBadge>;
}
