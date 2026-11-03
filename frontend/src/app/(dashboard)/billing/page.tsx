import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { BillingNavigation } from "@/features/billing/components/BillingNavigation";
import { RefundDueLookup } from "@/features/billing/components/RefundDueLookup";
import { InvoiceLookup } from "@/features/billing/components/InvoiceLookup";

export const metadata: Metadata = {
  title: "Viện phí | MediFlow",
};

export default function BillingPage() {
  const schedulingEnabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  return (
    <PageShell title="Viện phí" description="Tra cứu hóa đơn, khoản đã thu và nghĩa vụ hoàn tiền.">
      <RoleGate allowed={["ADMIN", "CASHIER"]}>
        <BillingNavigation schedulingEnabled={schedulingEnabled} />
        <InvoiceLookup />
        <RefundDueLookup />
      </RoleGate>
    </PageShell>
  );
}
