import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { BillingNavigation } from "@/features/billing/components/BillingNavigation";
import { RefundDueLookup, RegistrationSettlementList } from "@/features/billing/components/RefundDueLookup";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Hoàn tiền | MediFlow" };

export default function BillingRefundsPage() {
  const schedulingEnabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  return <PageShell title="Hoàn tiền" description="Tra cứu khoản cần hoàn và ghi nhận tiền đã hoàn cho người bệnh.">
    <RoleGate allowed={["ADMIN", "CASHIER"]}>
      <BillingNavigation schedulingEnabled={schedulingEnabled} />
      {process.env.MEDIFLOW_CLINIC_INTAKE_UI_ENABLED === "true" ? <RegistrationSettlementList /> : null}
      <RefundDueLookup />
    </RoleGate>
  </PageShell>;
}
