import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PharmacyPageShell } from "@/features/pharmacy/components/PharmacyPageShell";
import { PrescriptionLookup } from "@/features/pharmacy/components/prescription/PrescriptionLookup";

export const metadata: Metadata = {
  title: "Tra đơn thuốc | MediFlow",
};

export default function PrescriptionLookupPage() {
  return (
    <PharmacyPageShell
      title="Tra đơn thuốc"
      description="Mở chi tiết đơn thuốc bằng mã UUID đã biết."
    >
      <RoleGate allowed={["ADMIN", "DOCTOR", "PHARMACIST"]}>
        <PrescriptionLookup />
      </RoleGate>
    </PharmacyPageShell>
  );
}
