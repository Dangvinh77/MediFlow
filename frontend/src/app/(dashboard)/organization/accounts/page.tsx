import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { AccountAdmin } from "@/features/organization/components/AccountAdmin";
export default function Page() { return <PageShell title="Quản lý tài khoản" description="Tạo tài khoản và điều khiển trạng thái truy cập."><RoleGate allowed={["ADMIN"]}><AccountAdmin /></RoleGate></PageShell>; }
