import { AsyncState } from "@/components/ui/AsyncState";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { ReportWorkspace } from "@/features/report/components/ReportWorkspace";

export const metadata: Metadata = {
  title: "Báo cáo | MediFlow",
};

export const dynamic = "force-dynamic";

export default async function ReportsPage({ searchParams }: { searchParams: Promise<{ view?: string }> }) {
  const outpatientEnabled = process.env.MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED === "true";
  // In outpatient MVP the legacy aggregates stay empty; land on the reconciled outpatient report.
  if (outpatientEnabled && !(await searchParams).view) redirect("/reports/outpatient");
  return (
    <RoleGate allowed={["ADMIN", "MANAGER"]}>
      <Suspense fallback={<PageShell title="Báo cáo"><AsyncState kind="loading" message="Đang tải báo cáo…" /></PageShell>}>
        <ReportWorkspace outpatientEnabled={outpatientEnabled} />
      </Suspense>
    </RoleGate>
  );
}
