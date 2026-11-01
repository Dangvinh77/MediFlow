import { PageShell } from "@/components/layout/PageShell";
import { DepartmentManagementWorkspace } from "@/features/appointment/components/DepartmentManagementWorkspace";
export const dynamic = "force-dynamic";
export default function DepartmentDashboardPage() { return <PageShell title="Quản lý khoa" description="Buổi khám và phân công giờ làm việc trong khoa được bổ nhiệm."><DepartmentManagementWorkspace enabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"} /></PageShell>; }
