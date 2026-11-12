import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { LabCriticalNotices } from "@/features/lab/components/LabCriticalNotices";

export const metadata: Metadata = { title: "Cảnh báo kết quả nguy cấp | MediFlow" };

export default function LabCriticalPage() {
  return <PageShell title="Cảnh báo kết quả nguy cấp" description="Các kết quả nguy cấp đang chờ bác sĩ xác nhận đã nhận.">
    <RoleGate allowed={["DOCTOR", "LAB_TECH"]}><LabCriticalNotices /></RoleGate>
  </PageShell>;
}
