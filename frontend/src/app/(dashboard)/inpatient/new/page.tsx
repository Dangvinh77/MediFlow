import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { CreateAdmissionForm } from "@/features/inpatient/components/CreateAdmissionForm";

export const metadata: Metadata = { title: "Tạo đợt nội trú | MediFlow" };

export default function NewAdmissionPage() {
  return (
    <PageShell
      title="Tạo đợt nội trú"
      description="Tiếp nhận yêu cầu nhập viện đã được lập từ hồ sơ khám hoặc cấp cứu."
    >
      <RoleGate allowed={["ADMIN", "DOCTOR"]}>
        <CreateAdmissionForm />
      </RoleGate>
    </PageShell>
  );
}
