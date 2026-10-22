import { RecordBreadcrumb } from "@/features/organization/components/RecordLayout";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { StaffForm } from "@/features/organization/components/StaffForm";
export default function Page() { return <PageShell title="Thêm nhân sự" description="Tạo hồ sơ nhân sự mới."><RecordBreadcrumb href="/organization" parent="Tổ chức" current="Thêm nhân sự" /><RoleGate allowed={["ADMIN"]}><StaffForm /></RoleGate></PageShell>; }
