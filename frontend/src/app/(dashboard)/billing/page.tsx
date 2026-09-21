import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { InvoiceLookup } from "@/features/billing/components/InvoiceLookup";

export const metadata: Metadata = {
  title: "Viện phí | MediFlow",
};

export default function BillingPage() {
  return (
    <PageShell title="Viện phí" description="Tra cứu hóa đơn theo mã bệnh nhân.">
      <RoleGate allowed={["ADMIN", "CASHIER"]}>
        <InvoiceLookup />
      </RoleGate>
    </PageShell>
  );
}
