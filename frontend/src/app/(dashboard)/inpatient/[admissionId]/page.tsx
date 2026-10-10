import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { AdmissionDetail } from "@/features/inpatient/components/AdmissionDetail";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = { title: "Chi tiết nội trú | MediFlow" };

interface AdmissionDetailPageProps {
  params: Promise<{ admissionId: string }>;
  searchParams: Promise<{ notice?: string }>;
}

export default async function AdmissionDetailPage({ params, searchParams }: AdmissionDetailPageProps) {
  const { admissionId } = await params;
  const { notice } = await searchParams;
  const id = admissionId.trim();
  return (
    <PageShell title="Chi tiết đợt nội trú" description="Thông tin nhập viện và các tham chiếu nghiệp vụ liên quan.">
      {!isUuid(id) ? (
        <section role="alert" className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold">Mã đợt nội trú không hợp lệ</h2>
          <p className="mt-2 break-all">Giá trị <code>{id}</code> không phải UUID nên chưa gọi API.</p>
        </section>
      ) : (
        <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE", "CASHIER"]}>
            <AdmissionDetail admissionId={id} notice={notice === "created" ? "created" : undefined} />
        </RoleGate>
      )}
    </PageShell>
  );
}
