import Link from "next/link";
import { outpatientUiEnabled } from "@/lib/outpatient-ui";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { CreateLabRequestForm } from "@/features/lab/components/CreateLabRequestForm";

export const metadata: Metadata = { title: "Tạo yêu cầu xét nghiệm | MediFlow" };

export const dynamic = "force-dynamic";

export default function CreateLabRequestPage() {
  return (
    <PageShell title="Tạo yêu cầu xét nghiệm" description="Tạo chỉ định rõ ràng từ hồ sơ khám; không suy luận quyền tài chính.">
      <RoleGate allowed={["ADMIN", "DOCTOR"]}>
        {outpatientUiEnabled() ? <section className="mt-6 space-y-4 rounded-xl border border-border bg-surface p-6"><p role="status" className="text-sm">Lập chỉ định xét nghiệm từ hồ sơ đang khám trước khi gửi yêu cầu.</p><Link href="/records" className="inline-flex min-h-11 items-center text-sm text-primary underline">Mở hồ sơ khám</Link></section> : <CreateLabRequestForm />}
      </RoleGate>
    </PageShell>
  );
}
