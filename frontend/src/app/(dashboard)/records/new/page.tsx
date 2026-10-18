import Link from "next/link";
import { outpatientUiEnabled } from "@/lib/outpatient-ui";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { CreateMedicalRecordForm } from "@/features/medical-record/components/CreateMedicalRecordForm";

export const metadata: Metadata = { title: "Tạo hồ sơ khám | MediFlow" };

export const dynamic = "force-dynamic";

export default function CreateMedicalRecordPage() {
  return (
    <PageShell title="Tạo hồ sơ khám" description="Ghi nhận lần khám và chẩn đoán ban đầu theo hợp đồng Clinical.">
      <RoleGate allowed={["ADMIN", "DOCTOR"]}>
        {outpatientUiEnabled() ? <section className="mt-6 space-y-4 rounded-xl border border-border bg-surface p-6"><p role="status" className="text-sm">Hồ sơ được tạo khi bắt đầu khám từ lịch hẹn.</p><Link href="/appointments" className="inline-flex min-h-11 items-center text-sm text-primary underline">Mở lịch hẹn</Link></section> : <CreateMedicalRecordForm />}
      </RoleGate>
    </PageShell>
  );
}
