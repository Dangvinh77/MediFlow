import { PageShell } from "@/components/layout/PageShell";
import { DepartmentManagementWorkspace } from "@/features/appointment/components/DepartmentManagementWorkspace";
export const dynamic = "force-dynamic";
export default function Page() { return <PageShell title="Buổi khám & phòng" description="Buổi khám trong khoa được bổ nhiệm."><DepartmentManagementWorkspace sessions enabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"} /></PageShell>; }
