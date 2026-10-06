import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { CreateMedicalRecordForm } from "@/features/medical-record/components/CreateMedicalRecordForm";

export const metadata: Metadata = { title: "Tạo hồ sơ khám | MediFlow" };

export default function CreateMedicalRecordPage() {
  return (
    <PageShell title="Tạo hồ sơ khám" description="Ghi nhận lần khám và chẩn đoán ban đầu theo hợp đồng Clinical.">
      <RoleGate allowed={["ADMIN", "DOCTOR"]}>
        <CreateMedicalRecordForm />
      </RoleGate>
    </PageShell>
  );
}
