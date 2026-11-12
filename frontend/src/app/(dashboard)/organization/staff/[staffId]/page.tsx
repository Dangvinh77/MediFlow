
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import { RecordBreadcrumb } from "@/features/organization/components/RecordLayout";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { StaffDetail } from "@/features/organization/components/StaffDetail";
import { isUuid } from "@/lib/validation";
interface Props { params: Promise<{ staffId: string }>; searchParams: Promise<{ notice?: string }> }
export default async function Page({ params, searchParams }: Props) { const { staffId } = await params; const { notice } = await searchParams; const id = staffId.trim(); return <PageShell title="Chi tiết nhân sự" description="Hồ sơ chuyên môn và khoa công tác."><RecordBreadcrumb href="/organization" parent="Tổ chức" current="Chi tiết nhân sự" /><L10nValue>{!isUuid(id) ? <p role="alert" className="mt-6 text-danger"><L10n>{"Mã nhân sự không hợp lệ."}</L10n></p> : <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}><StaffDetail staffId={id} notice={notice === "created" || notice === "updated" ? notice : undefined} /></RoleGate>}</L10nValue></PageShell>; }
