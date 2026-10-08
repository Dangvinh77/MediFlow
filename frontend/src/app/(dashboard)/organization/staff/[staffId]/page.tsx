import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { StaffDetail } from "@/features/organization/components/StaffDetail";
import { isUuid } from "@/lib/validation";
interface Props { params: Promise<{ staffId: string }>; searchParams: Promise<{ notice?: string }> }
export default async function Page({ params, searchParams }: Props) { const { staffId } = await params; const { notice } = await searchParams; const id = staffId.trim(); return <PageShell title="Chi tiết nhân sự" description="Hồ sơ chuyên môn và khoa công tác.">{!isUuid(id) ? <p role="alert" className="mt-6 text-danger">Mã nhân sự không hợp lệ.</p> : <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE"]}><StaffDetail staffId={id} notice={notice === "created" || notice === "updated" ? notice : undefined} /></RoleGate>}</PageShell>; }
