import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PaymentRequestLookupForm } from "@/features/billing/components/PaymentRequestLookupForm";

export const metadata: Metadata = {
  title: "Thanh toán khoản phí | MediFlow",
};

export default function BillingPaymentRequestsPage() {
  return (
    <PageShell title="Thanh toán khoản phí đã chọn" description="Hoàn tất thanh toán một khoản phí khám/xét nghiệm/thuốc theo mã do bên phát sinh cung cấp.">
      <RoleGate allowed={["ADMIN", "CASHIER"]}>
        <PaymentRequestLookupForm />
      </RoleGate>
    </PageShell>
  );
}
