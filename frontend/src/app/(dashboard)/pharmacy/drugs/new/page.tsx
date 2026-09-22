import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { CreateDrugForm } from "@/features/pharmacy/components/drug/CreateDrugForm";

export const metadata: Metadata = {
  title: "Tạo thuốc | MediFlow",
};

/**
 * Route tạo thuốc mới.
 *
 * Page giữ là Server Component và chỉ composition form component.
 */
export default function CreateDrugPage() {
  return (
    <PageShell
      title="Tạo thuốc"
      description="Bổ sung thuốc mới vào danh mục."
    >
      <RoleGate allowed={["ADMIN", "PHARMACIST"]}>
        <CreateDrugForm />
      </RoleGate>
    </PageShell>
  );
}
