import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { StaffForm } from "@/features/organization/components/StaffForm";
export default function Page() { return <PageShell title="Thêm nhân sự" description="Tạo hồ sơ nhân sự mới."><RoleGate allowed={["ADMIN"]}><StaffForm /></RoleGate></PageShell>; }
