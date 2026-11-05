import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { RecordDetailWorkspace } from "../RecordDetailWorkspace";
import { isUuid } from "@/lib/validation";
import { outpatientUiEnabled } from "@/lib/outpatient-ui";
import { resolvePrescriptionMode } from "@/features/pharmacy/prescriptionMode";

export const metadata: Metadata = { title: "Chi tiết hồ sơ khám | MediFlow" };
export const dynamic = "force-dynamic";

interface RecordDetailPageProps {
  params: Promise<{ recordId: string }>;
  searchParams: Promise<{ notice?: string }>;
}

export default async function RecordDetailPage({ params, searchParams }: RecordDetailPageProps) {
  const { recordId } = await params;
  const { notice } = await searchParams;
  const id = recordId.trim();
  const prescriptionMode = resolvePrescriptionMode(process.env.MEDIFLOW_PHARMACY_PRESCRIPTION_MODE);

  return (
    <PageShell title="Chi tiết hồ sơ khám" description="Thông tin khám, chẩn đoán và hướng xử trí.">
      {!isUuid(id) ? (
        <section role="alert" className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold">Mã hồ sơ không hợp lệ</h2>
          <p className="mt-2 break-all">Giá trị <code>{id}</code> không phải UUID nên chưa gọi API.</p>
        </section>
      ) : (
        <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}>
          <RecordDetailWorkspace
            recordId={id}
            outpatientEnabled={outpatientUiEnabled()}
            schedulingV2Enabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"}
            prescriptionMode={prescriptionMode}
            notice={notice === "created" || notice === "updated" ? notice : undefined}
          />
        </RoleGate>
      )}
    </PageShell>
  );
}
