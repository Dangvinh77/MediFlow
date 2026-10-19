import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PrescriptionAdjustmentLookupForm } from "@/features/billing/components/PrescriptionAdjustmentLookupForm";

export const metadata: Metadata = {
  title: "Hoàn tiền đơn thuốc | MediFlow",
};

export default function BillingPrescriptionAdjustmentsPage() {
  return (
    <PageShell title="Hoàn tiền đơn thuốc" description="Tra cứu khoản hoàn tiền còn phải trả sau khi một đơn thuốc đã thu tiền bị từ chối hoặc huỷ.">
      <RoleGate allowed={["ADMIN", "CASHIER"]}>
        <PrescriptionAdjustmentLookupForm />
      </RoleGate>
    </PageShell>
  );
}
