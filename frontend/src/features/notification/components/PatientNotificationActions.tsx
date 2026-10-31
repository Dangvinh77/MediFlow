import Link from "next/link";

/** The public notification DTO has no source ID/type: link only to authorized own lists. */
export function PatientNotificationActions() {
  return <nav aria-label="Thông tin khám của tôi" className="flex flex-wrap gap-3">
    <Link href="/records/mine" className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Kết quả khám của tôi</Link>
    <Link href="/lab/mine" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Kết quả xét nghiệm của tôi</Link>
    <Link href="/appointments/my-registration" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Phiếu khám của tôi</Link>
  </nav>;
}
