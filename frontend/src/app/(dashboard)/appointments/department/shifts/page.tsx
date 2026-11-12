import { PageShell } from "@/components/layout/PageShell";
import { DepartmentManagementWorkspace } from "@/features/appointment/components/DepartmentManagementWorkspace";
export const dynamic = "force-dynamic";
export default function DepartmentShiftsPage() { return <PageShell title="Lịch làm việc bác sĩ" description="Phân công giờ, phòng và tạo buổi khám từ giờ làm việc."><DepartmentManagementWorkspace shifts clinicIntakeEnabled={process.env.MEDIFLOW_CLINIC_INTAKE_UI_ENABLED === "true"} enabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"} /></PageShell>; }
