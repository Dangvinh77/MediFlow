import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { SendNotificationForm } from "@/features/notification/components/SendNotificationForm";
export default function Page() { return <PageShell title="Gửi thông báo" description="Gửi thông báo thủ công qua kênh được chọn."><RoleGate allowed={["ADMIN"]}><SendNotificationForm /></RoleGate></PageShell>; }
