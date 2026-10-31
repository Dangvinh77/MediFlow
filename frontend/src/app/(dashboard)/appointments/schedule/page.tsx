import { isUuid } from "@/lib/validation";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { ScheduleManagementWorkspace } from "@/features/appointment/components/ScheduleManagementWorkspace";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Quản lý buổi khám | MediFlow" };

export default async function ScheduleManagementPage({ searchParams }: { searchParams: Promise<{ managerAccountId?: string | string[] }> }) {
  const query = await searchParams;
  const initialManagerAccountId = typeof query.managerAccountId === "string" && isUuid(query.managerAccountId) ? query.managerAccountId : undefined;
  const enabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  return <PageShell title="Quản lý buổi khám" description="Quản lý phạm vi khoa và lập buổi khám ngoại trú.">
    <RoleGate allowed={["ADMIN", "MANAGER"]}><ScheduleManagementWorkspace enabled={enabled} initialManagerAccountId={initialManagerAccountId} /></RoleGate>
  </PageShell>;
}
