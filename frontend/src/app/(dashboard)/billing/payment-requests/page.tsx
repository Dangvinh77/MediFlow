import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PaymentRequestWorkspace } from "@/features/billing/components/PaymentRequestWorkspace";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Thanh toán khoản phí | MediFlow",
};

export default function BillingPaymentRequestsPage() {
  const quoteEnabled = process.env.MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED === "true";
  const zeroLiabilityEnabled = quoteEnabled && process.env.MEDIFLOW_ZERO_PAYABLE_UI_ENABLED === "true";
  return (
    <PageShell title="Thu phí xét nghiệm và thuốc" description="Chọn bệnh nhân, đối chiếu khoản cần xử lý và thanh toán theo báo giá.">
      <RoleGate allowed={["ADMIN", "CASHIER"]}>
        <PaymentRequestWorkspace quoteEnabled={quoteEnabled} zeroLiabilityEnabled={zeroLiabilityEnabled} />
      </RoleGate>
    </PageShell>
  );
}
