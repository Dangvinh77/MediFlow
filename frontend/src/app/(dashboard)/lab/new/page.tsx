import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { CreateLabRequestForm } from "@/features/lab/components/CreateLabRequestForm";

export const metadata: Metadata = { title: "Tạo yêu cầu xét nghiệm | MediFlow" };

export default function CreateLabRequestPage() {
  return (
    <PageShell title="Tạo yêu cầu xét nghiệm" description="Tạo chỉ định rõ ràng từ hồ sơ khám; không suy luận quyền tài chính.">
      <RoleGate allowed={["ADMIN", "DOCTOR"]}>
        <CreateLabRequestForm />
      </RoleGate>
    </PageShell>
  );
}
