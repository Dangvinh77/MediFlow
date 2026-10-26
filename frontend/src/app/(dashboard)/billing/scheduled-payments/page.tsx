import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { ScheduledPaymentWorkspace } from "@/features/billing/components/ScheduledPaymentWorkspace";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Thu phí lịch khám | MediFlow" };

export default function ScheduledPaymentsPage() {
  const enabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  const quoteEnabled = process.env.MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED === "true";
  const zeroLiabilityEnabled = quoteEnabled && process.env.MEDIFLOW_ZERO_PAYABLE_UI_ENABLED === "true";
  return <PageShell title="Thu phí lịch khám" description="Tra cứu số dư do Billing xác nhận và theo dõi kết quả thu.">
    <RoleGate allowed={["ADMIN", "CASHIER"]}><ScheduledPaymentWorkspace enabled={enabled} quoteEnabled={quoteEnabled} zeroLiabilityEnabled={zeroLiabilityEnabled} /></RoleGate>
  </PageShell>;
}
