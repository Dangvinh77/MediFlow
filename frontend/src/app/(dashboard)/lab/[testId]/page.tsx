import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { LabDetail } from "@/features/lab/components/LabDetail";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = { title: "Chi tiết xét nghiệm | MediFlow" };

interface LabDetailPageProps {
  params: Promise<{ testId: string }>;
}

export default async function LabDetailPage({ params }: LabDetailPageProps) {
  const { testId } = await params;
  const id = testId.trim();

  return (
    <PageShell title="Chi tiết xét nghiệm" description="Thông tin yêu cầu, tài chính và kết quả xét nghiệm.">
      {!isUuid(id) ? (
        <section role="alert" className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold">Mã xét nghiệm không hợp lệ</h2>
          <p className="mt-2 break-all">Giá trị <code>{id}</code> không phải UUID nên chưa gọi API.</p>
        </section>
      ) : (
        <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}>
          <LabDetail testId={id} />
        </RoleGate>
      )}
    </PageShell>
  );
}
