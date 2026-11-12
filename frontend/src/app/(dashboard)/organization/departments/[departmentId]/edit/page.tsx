
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import { RecordBreadcrumb } from "@/features/organization/components/RecordLayout";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DepartmentForm } from "@/features/organization/components/DepartmentForm";
import { isUuid } from "@/lib/validation";
interface Props { params: Promise<{ departmentId: string }> }
export default async function Page({ params }: Props) { const { departmentId } = await params; const id = departmentId.trim(); return <PageShell title="Sửa khoa" description="Cập nhật thông tin và trưởng khoa."><RecordBreadcrumb href="/organization" parent="Tổ chức" current="Sửa khoa" /><L10nValue>{!isUuid(id) ? <p role="alert" className="mt-6 text-danger"><L10n>{"Mã khoa không hợp lệ."}</L10n></p> : <RoleGate allowed={["ADMIN"]}><DepartmentForm departmentId={id} /></RoleGate>}</L10nValue></PageShell>; }
