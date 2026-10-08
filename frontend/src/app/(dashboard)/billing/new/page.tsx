import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { InvoiceForm } from "@/features/billing/components/InvoiceForm";
export default function Page() { return <PageShell title="Lập hóa đơn" description="Tổng hợp các khoản phí chưa thanh toán của bệnh nhân."><RoleGate allowed={["ADMIN", "CASHIER"]}><InvoiceForm /></RoleGate></PageShell>; }
