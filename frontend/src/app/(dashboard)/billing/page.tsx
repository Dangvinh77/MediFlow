import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { BillingNavigation } from "@/features/billing/components/BillingNavigation";
import { InvoiceLookup } from "@/features/billing/components/InvoiceLookup";

export const metadata: Metadata = {
  title: "Lịch sử thu | MediFlow",
};

export default function BillingPage() {
  const schedulingEnabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  return (
    <PageShell title="Lịch sử thu" description="Tra cứu hóa đơn theo bệnh nhân.">
      <RoleGate allowed={["ADMIN", "CASHIER"]}>
        <BillingNavigation schedulingEnabled={schedulingEnabled} />
        <InvoiceLookup />
      </RoleGate>
    </PageShell>
  );
}
