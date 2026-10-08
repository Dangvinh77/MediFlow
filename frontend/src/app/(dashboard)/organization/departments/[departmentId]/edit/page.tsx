import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DepartmentForm } from "@/features/organization/components/DepartmentForm";
import { isUuid } from "@/lib/validation";
interface Props { params: Promise<{ departmentId: string }> }
export default async function Page({ params }: Props) { const { departmentId } = await params; const id = departmentId.trim(); return <PageShell title="Sửa khoa" description="Cập nhật thông tin và trưởng khoa.">{!isUuid(id) ? <p role="alert" className="mt-6 text-danger">Mã khoa không hợp lệ.</p> : <RoleGate allowed={["ADMIN"]}><DepartmentForm departmentId={id} /></RoleGate>}</PageShell>; }
