import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { StaffForm } from "@/features/organization/components/StaffForm";
import { isUuid } from "@/lib/validation";
interface Props { params: Promise<{ staffId: string }> }
export default async function Page({ params }: Props) { const { staffId } = await params; const id = staffId.trim(); return <PageShell title="Sửa nhân sự" description="Cập nhật chuyên môn, liên hệ và khoa công tác.">{!isUuid(id) ? <p role="alert" className="mt-6 text-danger">Mã nhân sự không hợp lệ.</p> : <RoleGate allowed={["ADMIN"]}><StaffForm staffId={id} /></RoleGate>}</PageShell>; }
