import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PaymentRequestLookupForm } from "@/features/billing/components/PaymentRequestLookupForm";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Thanh toán khoản phí | MediFlow",
};

export default function BillingPaymentRequestsPage() {
  const quoteEnabled = process.env.MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED === "true";
  const zeroLiabilityEnabled = quoteEnabled && process.env.MEDIFLOW_ZERO_PAYABLE_UI_ENABLED === "true";
  return (
    <PageShell title="Thanh toán khoản phí đã chọn" description="Hoàn tất thanh toán một khoản phí khám/xét nghiệm/thuốc theo mã do bên phát sinh cung cấp.">
      <RoleGate allowed={["ADMIN", "CASHIER"]}>
        <PaymentRequestLookupForm quoteEnabled={quoteEnabled} zeroLiabilityEnabled={zeroLiabilityEnabled} />
      </RoleGate>
    </PageShell>
  );
}
