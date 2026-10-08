import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { NotificationDetail } from "@/features/notification/components/NotificationDetail";
import { isUuid } from "@/lib/validation";
interface Props { params: Promise<{ notificationId: string }>; searchParams: Promise<{ notice?: string }> }
export default async function Page({ params, searchParams }: Props) { const { notificationId } = await params; const { notice } = await searchParams; const id = notificationId.trim(); return <PageShell title="Chi tiết thông báo" description="Nội dung và trạng thái gửi.">{!isUuid(id) ? <p role="alert" className="mt-6 text-danger">Mã thông báo không hợp lệ.</p> : <RoleGate allowed={["ADMIN", "PATIENT"]}><NotificationDetail notificationId={id} notice={notice === "sent" ? "sent" : undefined} /></RoleGate>}</PageShell>; }
