import { PageShell } from "@/components/layout/PageShell";
import { DepartmentManagementWorkspace } from "@/features/appointment/components/DepartmentManagementWorkspace";
export const dynamic = "force-dynamic";
export default function DepartmentDashboardPage() { return <PageShell title="Quản lý khoa" description="Tình hình khoa và lịch cả tuần, chỉ để xem. Việc thay đổi nằm ở Lịch làm việc bác sĩ và Buổi khám & phòng."><DepartmentManagementWorkspace enabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"} /></PageShell>; }
