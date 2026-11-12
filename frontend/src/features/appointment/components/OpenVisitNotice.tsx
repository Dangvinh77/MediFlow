"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useState } from "react";
import { ReferenceText } from "@/components/references/ReferenceText";
import { formatLocalDate } from "@/lib/format";
import { appointmentApi } from "../api";
import type { OpenVisitDTO } from "../types";

interface OpenVisitNoticeProps {
  patientId: string;
  departmentId: string;
  reason: string;
  onReasonChange: (value: string) => void;
  onSameDepartmentChange: (sameDepartment: boolean) => void;
  /** The server refused for an open visit this notice could not see. */
  forceReason?: boolean;
  disabled?: boolean;
}

/** Warns reception about unfinished examinations. A failed check never blocks: the server decides on submit. */
export function OpenVisitNotice({ patientId, departmentId, reason, onReasonChange, onSameDepartmentChange, forceReason = false, disabled = false }: OpenVisitNoticeProps) {
  const { localize } = useLocale();
  const [state, setState] = useState<{ patientId: string; visits?: OpenVisitDTO[]; failed?: boolean } | null>(null);
  useEffect(() => {
    let active = true;
    appointmentApi.openVisits(patientId)
      .then(visits => { if (active) setState({ patientId, visits }); })
      .catch(() => { if (active) setState({ patientId, failed: true }); });
    return () => { active = false; };
  }, [patientId]);
  const current = state?.patientId === patientId ? state : null;
  const visits = current?.visits ?? [];
  const sameDepartment = visits.some(visit => visit.departmentId === departmentId);
  const loaded = current !== null;
  useEffect(() => {
    if (loaded) onSameDepartmentChange(sameDepartment);
  }, [loaded, onSameDepartmentChange, sameDepartment]);

  const askReason = sameDepartment || forceReason;
  if (!askReason && !current?.failed && visits.length === 0) return null;
  return <section aria-label={localize("Lượt khám đang mở")} className="space-y-3 rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm sm:col-span-2">
    <L10nValue>{current?.failed ? <p role="status"><L10n>{"Không kiểm tra được lượt khám đang mở của bệnh nhân. Vẫn có thể đăng ký, hệ thống sẽ kiểm tra khi gửi."}</L10n></p> : null}</L10nValue>
    <L10nValue>{visits.length ? <>
      <p role="status" className="font-medium"><L10n>{"Bệnh nhân còn "}</L10n>{visits.length}<L10n>{" lượt khám chưa kết thúc."}</L10n></p>
      <ul className="space-y-1"><L10nValue>{visits.map(visit => <li key={visit.registrationId}>
        <ReferenceText kind="department" value={visit.departmentId} fallback="Khoa" /> · <L10nValue>{formatLocalDate(visit.sessionDate)}</L10nValue><L10nValue>{visit.departmentId === departmentId ? " · Cùng khoa đang đăng ký" : ""}</L10nValue>
      </li>)}</L10nValue></ul>
    </> : null}</L10nValue>
    <L10nValue>{askReason ? <label className="block space-y-1 font-medium" htmlFor="reception-independent-reason"><L10n>{"Lý do đăng ký thêm lượt trong khoa này"}</L10n><textarea id="reception-independent-reason" value={reason} maxLength={500} disabled={disabled} onChange={event => onReasonChange(event.target.value)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2 font-normal" />
    </label> : null}</L10nValue>
  </section>;
}
