
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
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
        <p className="text-xs font-medium text-muted-foreground"><L10n>{"Bệnh nhân · họ tên và ngày sinh"}</L10n></p>
        <p className="font-semibold"><ReferenceText kind="patient" value={patientId} fallback="Bệnh nhân" /></p>
        <p className="text-sm text-muted-foreground"><L10nValue>{dateLabel}</L10nValue>: <L10nValue>{formatLocalDate(date)}</L10nValue>
          <L10nValue>{departmentId ? <> · <ReferenceText kind="department" value={departmentId} fallback="Khoa" /></> : null}</L10nValue>
          <L10nValue>{doctorId ? <> · <ReferenceText kind="doctor" value={doctorId} fallback="Bác sĩ" /></> : null}</L10nValue>
        </p>
      </div>
      <L10nValue>{children}</L10nValue>
    </div>
  </section>;
}
