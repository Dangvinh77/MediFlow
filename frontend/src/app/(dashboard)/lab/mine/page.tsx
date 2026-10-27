import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PatientLabResults } from "@/features/lab/components/PatientLabResults";
export const metadata = { title: "Kết quả xét nghiệm của tôi | MediFlow" };
export const dynamic = "force-dynamic";
export default function MyLabResultsPage() {
  return <PageShell title="Kết quả xét nghiệm của tôi" description="Các xét nghiệm đã hoàn tất của bạn.">
    <RoleGate allowed={["PATIENT"]}><PatientLabResults /></RoleGate>
  </PageShell>;
}
