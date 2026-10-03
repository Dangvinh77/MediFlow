import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { MedicalRecordDetail } from "@/features/medical-record/components/MedicalRecordDetail";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = { title: "Chi tiết hồ sơ khám | MediFlow" };

interface RecordDetailPageProps {
  params: Promise<{ recordId: string }>;
}

export default async function RecordDetailPage({ params }: RecordDetailPageProps) {
  const { recordId } = await params;
  const id = recordId.trim();

  return (
    <PageShell title="Chi tiết hồ sơ khám" description="Thông tin khám, chẩn đoán và hướng xử trí.">
      {!isUuid(id) ? (
        <section role="alert" className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold">Mã hồ sơ không hợp lệ</h2>
          <p className="mt-2 break-all">Giá trị <code>{id}</code> không phải UUID nên chưa gọi API.</p>
        </section>
      ) : (
        <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}>
          <MedicalRecordDetail recordId={id} />
        </RoleGate>
      )}
    </PageShell>
  );
}
