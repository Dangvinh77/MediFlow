import { AsyncState } from "@/components/ui/AsyncState";
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
      <Suspense fallback={<PageShell title="Báo cáo ngoại trú"><AsyncState kind="loading" message="Đang tải báo cáo…" /></PageShell>}>
        <OutpatientReportWorkspace enabled={process.env.MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED === "true"} />
      </Suspense>
    </RoleGate>
  );
}
