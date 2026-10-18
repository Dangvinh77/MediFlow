import Link from "next/link";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { outpatientUiEnabled } from "@/lib/outpatient-ui";
import { isUuid } from "@/lib/validation";
import { LabOrderWorkspace } from "./LabOrderWorkspace";

export const metadata: Metadata = { title: "Chỉ định xét nghiệm | MediFlow" };
export const dynamic = "force-dynamic";

export default async function LabOrderPage({ params }: { params: Promise<{ recordId: string }> }) {
  const { recordId } = await params;
  return <PageShell title="Chỉ định xét nghiệm" description="Lập chỉ định từ hồ sơ và gửi yêu cầu đến xét nghiệm.">
    {!outpatientUiEnabled() ? <p role="status" className="mt-6 text-sm text-muted-foreground">Chức năng chưa được mở tại cơ sở.</p>
      : !isUuid(recordId) ? <p role="alert" className="mt-6 text-sm text-danger">Mã hồ sơ không hợp lệ.</p>
        : <RoleGate allowed={["ADMIN", "DOCTOR"]}><LabOrderWorkspace key={recordId} recordId={recordId} /></RoleGate>}
    {isUuid(recordId) ? <Link href={`/records/${recordId}`} className="mt-6 inline-flex min-h-11 items-center text-sm text-primary underline">Quay lại hồ sơ khám</Link> : null}
  </PageShell>;
}
