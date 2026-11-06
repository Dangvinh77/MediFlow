import { notFound } from "next/navigation";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { RecordBreadcrumb } from "@/features/organization/components/RecordLayout";
import { isUuid } from "@/lib/validation";
import { AccountDetailWorkspace } from "./AccountDetailWorkspace";

export default async function Page({ params }: { params: Promise<{ accountId: string }> }) {
  const { accountId } = await params;
  if (!isUuid(accountId)) notFound();
  return <PageShell title="Chi tiết tài khoản" description="Xem thông tin, đổi mật khẩu và quản lý trạng thái truy cập."><RecordBreadcrumb href="/organization/accounts" parent="Danh sách tài khoản" current="Chi tiết tài khoản" /><RoleGate allowed={["ADMIN"]}><AccountDetailWorkspace accountId={accountId} /></RoleGate></PageShell>;
}
