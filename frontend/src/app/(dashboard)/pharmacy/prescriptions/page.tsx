import { AsyncState } from "@/components/ui/AsyncState";
import type { Metadata } from "next";
import { Suspense } from "react";
import { RoleGate } from "@/components/auth/RoleGate";
import { PharmacyPageShell } from "@/features/pharmacy/components/PharmacyPageShell";
import { PrescriptionList } from "@/features/pharmacy/components/prescription/PrescriptionList";

export const metadata: Metadata = {
  title: "Đơn thuốc | MediFlow",
};

export default function PrescriptionLookupPage() {
  return (
    <PharmacyPageShell
      title="Đơn thuốc"
      description="Xem thuốc đã kê, tình trạng thanh toán và cấp phát."
    >
      <RoleGate allowed={["ADMIN", "DOCTOR", "PHARMACIST"]}>
        <Suspense fallback={<AsyncState kind="loading" message="Đang tải danh sách đơn thuốc…" />}>
          <PrescriptionList />
        </Suspense>
      </RoleGate>
    </PharmacyPageShell>
  );
}
