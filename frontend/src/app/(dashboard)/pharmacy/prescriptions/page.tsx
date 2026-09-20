import type { Metadata } from "next";
import { PageShell } from "@/components/layout/PageShell";
import { PrescriptionLookup } from "@/features/pharmacy/components/prescription/PrescriptionLookup";

export const metadata: Metadata = {
  title: "Tra đơn thuốc | MediFlow",
};

export default function PrescriptionLookupPage() {
  return (
    <PageShell
      title="Tra đơn thuốc"
      description="Mở chi tiết đơn thuốc bằng mã UUID đã biết."
    >
      <PrescriptionLookup />
    </PageShell>
  );
}
