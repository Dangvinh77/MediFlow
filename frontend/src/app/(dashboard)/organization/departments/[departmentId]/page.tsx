import { RecordBreadcrumb } from "@/features/organization/components/RecordLayout";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DepartmentDetail } from "@/features/organization/components/DepartmentDetail";
import { isUuid } from "@/lib/validation";
interface Props { params: Promise<{ departmentId: string }>; searchParams: Promise<{ notice?: string }> }
export default async function Page({ params, searchParams }: Props) {
  const { departmentId } = await params;
  const { notice } = await searchParams;
  const id = departmentId.trim();
  if (!isUuid(id)) return <PageShell title="Chi tiết khoa/phòng" description="Thông tin tổ chức và trạng thái vận hành."><RecordBreadcrumb href="/organization" parent="Tổ chức" current="Chi tiết khoa/phòng" /><p role="alert" className="mt-6 text-danger">Mã khoa/phòng không hợp lệ.</p></PageShell>;
  return <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE"]}><DepartmentDetail departmentId={id} notice={notice === "created" || notice === "updated" ? notice : undefined} /></RoleGate>;
}
