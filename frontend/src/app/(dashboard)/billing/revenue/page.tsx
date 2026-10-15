import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { RevenueReport } from "@/features/billing/components/RevenueReport";

export const metadata: Metadata = {
  title: "Doanh thu theo khoa | MediFlow",
};

export default function BillingRevenuePage() {
  return (
    <PageShell title="Doanh thu theo khoa" description="Doanh thu đã thanh toán, gộp theo khoa trong khoảng ngày.">
      <RoleGate allowed={["ADMIN", "MANAGER"]}>
        <RevenueReport />
      </RoleGate>
    </PageShell>
  );
}
