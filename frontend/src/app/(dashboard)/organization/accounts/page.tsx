import { RecordBreadcrumb } from "@/features/organization/components/RecordLayout";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { AccountWorkspace } from "./AccountWorkspace";
export default function Page() { return <PageShell title="Quản lý tài khoản" description="Tạo tài khoản và điều khiển trạng thái truy cập."><RecordBreadcrumb href="/organization" parent="Tổ chức" current="Quản lý tài khoản" /><RoleGate allowed={["ADMIN"]}><AccountWorkspace /></RoleGate></PageShell>; }
