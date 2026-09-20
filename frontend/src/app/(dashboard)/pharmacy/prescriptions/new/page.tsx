import type { Metadata } from "next";
import { PageShell } from "@/components/layout/PageShell";
import { CreatePrescriptionForm } from "@/features/pharmacy/components/prescription/CreatePrescriptionForm";

export const metadata: Metadata = {
  title: "Kê đơn | MediFlow",
};

export default function CreatePrescriptionPage() {
  return (
    <PageShell
      title="Kê đơn"
      description="Tạo đơn thuốc và khai báo các dòng thuốc."
    >
      <CreatePrescriptionForm />
    </PageShell>
  );
}
