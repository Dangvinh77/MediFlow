import type { Metadata } from "next";
import { Suspense } from "react";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { OutpatientReportWorkspace } from "@/features/report/components/OutpatientReportWorkspace";

export const metadata: Metadata = { title: "Báo cáo ngoại trú | MediFlow" };
export const dynamic = "force-dynamic";

export default function OutpatientReportsPage() {
  return (
    <RoleGate allowed={["ADMIN", "MANAGER"]}>
      <Suspense fallback={<PageShell title="Báo cáo ngoại trú"><p role="status" className="mt-6 text-sm text-muted-foreground">Đang tải báo cáo…</p></PageShell>}>
        <OutpatientReportWorkspace enabled={process.env.MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED === "true"} />
      </Suspense>
    </RoleGate>
  );
}
