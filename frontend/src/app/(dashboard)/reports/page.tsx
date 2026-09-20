import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DailyReportView } from "@/features/report/components/DailyReportView";

export const metadata: Metadata = {
  title: "Báo cáo | MediFlow",
};

export default function ReportsPage() {
  return (
    <PageShell
      title="Báo cáo"
      description="Xem báo cáo hoạt động hàng ngày theo ngày và khoa."
    >
      <RoleGate allowed={["ADMIN", "MANAGER"]}>
        <DailyReportView />
      </RoleGate>
    </PageShell>
  );
}
