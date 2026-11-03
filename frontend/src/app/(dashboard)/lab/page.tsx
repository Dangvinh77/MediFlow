import { outpatientUiEnabled } from "@/lib/outpatient-ui";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { LabTable } from "@/features/lab/components/LabTable";
import type { LabTestStatus } from "@/features/lab/types";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = { title: "Xét nghiệm | MediFlow" };

export const dynamic = "force-dynamic";

export default async function LabPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const params = await searchParams;
  const episodeId = typeof params.episodeId === "string" ? params.episodeId : "";
  const episodeType = params.episodeType === "ADMISSION" ? "ADMISSION" : "OUTPATIENT_VISIT";
  const statuses: string[] = ["PENDING", "AWAITING_PAYMENT", "READY", "IN_PROGRESS", "COMPLETED", "CANCELLED"];
  const status = params.status === "all" ? "" : typeof params.status === "string" && statuses.includes(params.status) ? params.status as LabTestStatus : undefined;
  const departmentId = typeof params.departmentId === "string" && isUuid(params.departmentId) ? params.departmentId : "";
  const page = typeof params.page === "string" && /^\d{1,6}$/.test(params.page) ? Number(params.page) : 0;
  return (
    <PageShell
      title="Xét nghiệm"
      description="Hàng đợi xét nghiệm theo khoa, trạng thái và đợt chăm sóc."
    >
      <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE", "LAB_TECH"]}>
        <LabTable key={`${episodeType}:${episodeId}:${status}:${departmentId}:${page}`} initialEpisodeId={episodeId} initialEpisodeType={episodeType} initialStatus={status} initialDepartmentId={departmentId} initialPage={page} outpatientEnabled={outpatientUiEnabled()} />
      </RoleGate>
    </PageShell>
  );
}
