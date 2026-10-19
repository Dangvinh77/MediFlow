import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PrescriptionAdjustmentRefundDue } from "@/features/billing/components/PrescriptionAdjustmentRefundDue";
import { isUuid } from "@/lib/validation";

interface Props {
  params: Promise<{ prescriptionId: string }>;
}

export default async function Page({ params }: Props) {
  const { prescriptionId } = await params;
  const id = prescriptionId.trim();

  return (
    <PageShell title="Hoàn tiền đơn thuốc" description="Xem và ghi nhận hoàn tiền cho các giao dịch bị ảnh hưởng bởi đơn thuốc này.">
      {!isUuid(id) ? (
        <p role="alert" className="mt-6 text-danger">Mã đơn thuốc không hợp lệ.</p>
      ) : (
        <RoleGate allowed={["ADMIN", "CASHIER"]}>
          <PrescriptionAdjustmentRefundDue prescriptionId={id} />
        </RoleGate>
      )}
    </PageShell>
  );
}
