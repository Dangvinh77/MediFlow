import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { MyWorkShiftsWorkspace } from "@/features/appointment/components/MyWorkShiftsWorkspace";
export const dynamic = "force-dynamic";
export default function MyWorkShiftsPage() { return <PageShell title="Lịch làm việc của tôi" description="Giờ và phòng được phân công theo ngày hoặc tuần."><RoleGate allowed={["DOCTOR"]}><MyWorkShiftsWorkspace enabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"} /></RoleGate></PageShell>; }
