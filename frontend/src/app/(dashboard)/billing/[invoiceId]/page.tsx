import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { InvoiceDetail } from "@/features/billing/components/InvoiceDetail";
import { isUuid } from "@/lib/validation";
interface Props { params: Promise<{ invoiceId: string }>; searchParams: Promise<{ notice?: string }> }
export default async function Page({ params, searchParams }: Props) { const { invoiceId } = await params; const { notice } = await searchParams; const id = invoiceId.trim(); return <PageShell title="Chi tiết hóa đơn" description="Các khoản phí và trạng thái thanh toán.">{!isUuid(id) ? <p role="alert" className="mt-6 text-danger">Mã hóa đơn không hợp lệ.</p> : <RoleGate allowed={["ADMIN", "CASHIER"]}><InvoiceDetail invoiceId={id} notice={notice === "created" ? "created" : undefined} /></RoleGate>}</PageShell>; }
