import type { Metadata } from "next";
import { Suspense } from "react";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { ReportWorkspace } from "@/features/report/components/ReportWorkspace";

export const metadata: Metadata = {
  title: "Báo cáo | MediFlow",
};

export default function ReportsPage() {
  return (
    <RoleGate allowed={["ADMIN", "MANAGER"]}>
      <Suspense fallback={<PageShell title="Báo cáo"><p role="status" className="mt-6 text-sm text-muted-foreground">Đang tải báo cáo…</p></PageShell>}>
        <ReportWorkspace />
      </Suspense>
    </RoleGate>
  );
}
