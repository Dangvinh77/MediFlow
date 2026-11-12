
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { LabDetail } from "@/features/lab/components/LabDetail";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = { title: "Chi tiết xét nghiệm | MediFlow" };

interface LabDetailPageProps {
  params: Promise<{ testId: string }>;
  searchParams: Promise<{ notice?: string; returnTo?: string }>;
}

export default async function LabDetailPage({ params, searchParams }: LabDetailPageProps) {
  const { testId } = await params;
  const { notice, returnTo } = await searchParams;
  const id = testId.trim();

  return (
    <PageShell title="Chi tiết xét nghiệm" description="Thông tin yêu cầu, tài chính và kết quả xét nghiệm.">
      <L10nValue>{!isUuid(id) ? (
        <section role="alert" className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold"><L10n>{"Mã xét nghiệm không hợp lệ"}</L10n></h2>
          <p className="mt-2 break-all"><L10n>{"Giá trị "}</L10n><code>{id}</code><L10n>{" không phải UUID nên chưa gọi API."}</L10n></p>
        </section>
      ) : (
        <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE", "LAB_TECH"]}>
          <LabDetail testId={id} notice={notice === "created" ? "created" : undefined} returnTo={returnTo} />
        </RoleGate>
      )}</L10nValue>
    </PageShell>
  );
}
