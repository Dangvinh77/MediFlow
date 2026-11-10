"use client";

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
  return <section aria-label="Lượt khám đang mở" className="space-y-3 rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm sm:col-span-2">
    {current?.failed ? <p role="status">Không kiểm tra được lượt khám đang mở của bệnh nhân. Vẫn có thể đăng ký, hệ thống sẽ kiểm tra khi gửi.</p> : null}
    {visits.length ? <>
      <p role="status" className="font-medium">Bệnh nhân còn {visits.length} lượt khám chưa kết thúc.</p>
      <ul className="space-y-1">{visits.map(visit => <li key={visit.registrationId}>
        <ReferenceText kind="department" value={visit.departmentId} fallback="Khoa" /> · {formatLocalDate(visit.sessionDate)}{visit.departmentId === departmentId ? " · Cùng khoa đang đăng ký" : ""}
      </li>)}</ul>
    </> : null}
    {askReason ? <label className="block space-y-1 font-medium" htmlFor="reception-independent-reason">Lý do đăng ký thêm lượt trong khoa này
      <textarea id="reception-independent-reason" value={reason} maxLength={500} disabled={disabled} onChange={event => onReasonChange(event.target.value)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2 font-normal" />
    </label> : null}
  </section>;
}
