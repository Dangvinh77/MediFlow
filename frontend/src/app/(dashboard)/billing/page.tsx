import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { BillingNavigation } from "@/features/billing/components/BillingNavigation";
import { PaymentHistoryLookup } from "@/features/billing/components/PaymentHistoryLookup";

export const metadata: Metadata = {
  title: "Lịch sử thu | MediFlow",
};

export default function BillingPage() {
  const schedulingEnabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  return (
    <PageShell title="Lịch sử thu" description="Các khoản đã thu và đã hoàn, mới nhất trước. Chọn bệnh nhân để lọc.">
      <RoleGate allowed={["ADMIN", "CASHIER"]}>
        <BillingNavigation schedulingEnabled={schedulingEnabled} />
        <PaymentHistoryLookup />
      </RoleGate>
    </PageShell>
  );
}
