import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PatientExamResults } from "@/features/medical-record/components/PatientExamResults";
export const metadata = { title: "Kết quả khám của tôi | MediFlow" };
export const dynamic = "force-dynamic";
export default function MyResultsPage() {
  return <PageShell title="Kết quả khám của tôi" description="Kết quả khám đã hoàn tất và hướng dẫn tái khám của bạn.">
    <RoleGate allowed={["PATIENT"]}><PatientExamResults schedulingEnabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"} /></RoleGate>
  </PageShell>;
}
