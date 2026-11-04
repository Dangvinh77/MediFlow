"use client";

import Link from "next/link";
import { ProfileAccountPanel } from "./ProfileAccountPanel";

export function PatientAccountIssuance({ patientId, onProfileChanged }: { patientId: string; onProfileChanged?: () => void }) {
  return <>
    <ProfileAccountPanel ownerType="patient" ownerId={patientId} onProfileChanged={onProfileChanged} />
    <section className="mt-4 rounded-xl border border-border bg-surface p-5">
      <h2 className="font-semibold">Đăng ký khám</h2>
      <p className="mt-1 text-sm text-muted-foreground">Bệnh nhân chưa cần đăng nhập để tiếp tục khám. Tài khoản chỉ dùng để xem kết quả và thông báo.</p>
      <Link className="mt-3 inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground" href={`/appointments/reception?patientId=${encodeURIComponent(patientId)}`}>Tiếp tục đăng ký khám</Link>
    </section>
  </>;
}
