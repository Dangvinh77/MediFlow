import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { AppointmentWorkspace } from "./AppointmentWorkspace";
import { outpatientUiEnabled } from "@/lib/outpatient-ui";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = { title: "Chi tiết lịch hẹn | MediFlow" };
export const dynamic = "force-dynamic";

interface AppointmentDetailPageProps {
  params: Promise<{ appointmentId: string }>;
}

export default async function AppointmentDetailPage({ params }: AppointmentDetailPageProps) {
  const { appointmentId } = await params;
  const id = appointmentId.trim();

  return (
    <PageShell
      title="Chi tiết lịch hẹn"
      description="Thông tin lịch khám, trạng thái và các mốc trong tiến trình khám."
    >
      {!isUuid(id) ? (
        <section role="alert" className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold">Mã lịch hẹn không hợp lệ</h2>
          <p className="mt-2 break-all">
            Giá trị <code>{id}</code> không phải UUID nên chưa gọi API.
          </p>
        </section>
      ) : (
        <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}>
          <AppointmentWorkspace appointmentId={id} outpatientEnabled={outpatientUiEnabled()} />
        </RoleGate>
      )}
    </PageShell>
  );
}
