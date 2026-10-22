import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { RecordBreadcrumb } from "@/features/organization/components/RecordLayout";
import { AccountCreateWorkspace } from "./AccountCreateWorkspace";

export default function Page() {
  return <PageShell title="Tạo tài khoản" description="Liên kết tài khoản mới với nhân viên hoặc bệnh nhân."><RecordBreadcrumb href="/organization/accounts" parent="Danh sách tài khoản" current="Tạo tài khoản" /><RoleGate allowed={["ADMIN"]}><AccountCreateWorkspace /></RoleGate></PageShell>;
}
