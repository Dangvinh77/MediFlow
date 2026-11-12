
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { EditMedicalRecordForm } from "@/features/medical-record/components/EditMedicalRecordForm";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = { title: "Sửa hồ sơ khám | MediFlow" };

interface EditRecordPageProps {
  params: Promise<{ recordId: string }>;
}

export default async function EditRecordPage({ params }: EditRecordPageProps) {
  const { recordId } = await params;
  const id = recordId.trim();
  return (
    <PageShell title="Sửa hồ sơ khám" description="Cập nhật triệu chứng của hồ sơ đang mở.">
      <L10nValue>{!isUuid(id) ? (
        <section role="alert" className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold"><L10n>{"Mã hồ sơ không hợp lệ"}</L10n></h2>
          <p className="mt-2 break-all"><L10n>{"Giá trị "}</L10n><code>{id}</code><L10n>{" không phải UUID nên chưa gọi API."}</L10n></p>
        </section>
      ) : (
        <RoleGate allowed={["ADMIN", "DOCTOR"]}>
          <EditMedicalRecordForm recordId={id} />
        </RoleGate>
      )}</L10nValue>
    </PageShell>
  );
}
