import { PageShell } from "@/components/layout/PageShell";
import { DepartmentManagementWorkspace } from "@/features/appointment/components/DepartmentManagementWorkspace";
export const dynamic = "force-dynamic";
export default async function Page({searchParams}: {searchParams: Promise<{date?: string | string[]}>}) {
  const query = await searchParams;
  return <PageShell title="Buổi khám & phòng" description="Lập, sửa, ngừng buổi nhận bệnh nhân: giờ, phòng và sức chứa."><DepartmentManagementWorkspace sessions clinicIntakeEnabled={process.env.MEDIFLOW_CLINIC_INTAKE_UI_ENABLED === "true"} initialDate={typeof query.date === "string" ? query.date : undefined} enabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"} /></PageShell>;
}
