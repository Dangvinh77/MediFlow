import type { ReactNode } from "react";
import { formatLocalDate } from "@/lib/format";
import { ReferenceText } from "./ReferenceText";

/** Presentation only: labels never establish clinical or financial authority. */
export function PatientContextBanner({ label, patientId, departmentId, doctorId, date, dateLabel, children, className = "" }: {
  label: string;
  patientId: string;
  departmentId?: string | null;
  doctorId?: string | null;
  date: string;
  dateLabel: string;
  children?: ReactNode;
  className?: string;
}) {
  return <section aria-label={label} className={`rounded-xl border border-border bg-surface p-4 shadow-sm ${className}`}>
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0 space-y-1">
        <p className="text-xs font-medium text-muted-foreground">Bệnh nhân · họ tên và ngày sinh</p>
        <p className="font-semibold"><ReferenceText kind="patient" value={patientId} fallback="Bệnh nhân" /></p>
        <p className="text-sm text-muted-foreground">{dateLabel}: {formatLocalDate(date)}
          {departmentId ? <> · <ReferenceText kind="department" value={departmentId} fallback="Khoa" /></> : null}
          {doctorId ? <> · <ReferenceText kind="doctor" value={doctorId} fallback="Bác sĩ" /></> : null}
        </p>
      </div>
      {children}
    </div>
  </section>;
}
