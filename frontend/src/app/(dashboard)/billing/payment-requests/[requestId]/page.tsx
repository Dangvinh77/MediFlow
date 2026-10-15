import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { LedgerPaymentForm } from "@/features/billing/components/LedgerPaymentForm";
import { isUuid } from "@/lib/validation";

interface Props {
  params: Promise<{ requestId: string }>;
}

export default async function Page({ params }: Props) {
  const { requestId } = await params;
  const id = requestId.trim();

  return (
    <PageShell title="Thanh toán khoản phí" description="Xác nhận số tiền và hình thức thanh toán cho khoản phí đã chọn.">
      {!isUuid(id) ? (
        <p role="alert" className="mt-6 text-danger">Mã khoản phí không hợp lệ.</p>
      ) : (
        <RoleGate allowed={["ADMIN", "CASHIER"]}>
          <LedgerPaymentForm requestId={id} />
        </RoleGate>
      )}
    </PageShell>
  );
}
