"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { ReferenceText } from "@/components/references/ReferenceText";
import { ApiRequestError } from "@/lib/api";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { appointmentApi } from "../api";
import { vietnamToday } from "../session-dates";
import type { OpenVisitDTO } from "../types";

type PanelState = { visits?: OpenVisitDTO[]; message?: string; correlationId?: string | null };

/** Examinations the doctor started and has not ended, including ones left open on earlier days. */
export function DoctorOpenVisitsPanel({ enabled }: { enabled: boolean }) {
  const { localize } = useLocale();
  const [state, setState] = useState<PanelState>({});
  const load = useCallback((isActive: () => boolean) => {
    appointmentApi.myOpenVisits()
      .then(visits => { if (isActive()) setState({ visits }); })
      .catch((cause: unknown) => {
        if (isActive()) setState({ message: cause instanceof Error ? cause.message : "Không thể tải lượt khám chưa kết thúc.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
      });
  }, []);
  useEffect(() => {
    if (!enabled) return;
    let active = true;
    load(() => active);
    return () => { active = false; };
  }, [enabled, load]);
  if (!enabled) return null;
  const today = vietnamToday();
  return <section aria-labelledby="doctor-open-visits" className="mb-6 space-y-3 rounded-xl border border-border bg-surface p-5">
    <h2 id="doctor-open-visits" className="text-lg font-semibold"><L10n>{"Lượt khám chưa kết thúc"}</L10n></h2>
    <L10nValue>{state.message ? <AsyncState kind="error" message={localize(state.message)} correlationId={state.correlationId} onRetry={() => { setState({}); load(() => true); }} />
      : !state.visits ? <AsyncState kind="loading" message={localize("Đang tải lượt khám chưa kết thúc…")} />
        : state.visits.length === 0 ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Không có lượt khám chưa kết thúc."}</L10n></p>
          : <ul className="divide-y divide-border"><L10nValue>{state.visits.map(visit => <li key={visit.registrationId} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
            <div>
              <p className="font-medium"><L10nValue>{formatLocalDate(visit.sessionDate)}</L10nValue><L10nValue>{visit.sessionDate < today ? " · Từ ngày trước" : ""}</L10nValue></p>
              <p className="text-muted-foreground"><ReferenceText kind="department" value={visit.departmentId} fallback="Khoa" /><L10nValue>{visit.startedAt ? ` · Bắt đầu ${formatInstant(visit.startedAt)}` : ""}</L10nValue></p>
            </div>
            <Link className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground" href={`/records/${encodeURIComponent(visit.recordId)}`}><L10n>{"Tiếp tục khám"}</L10n></Link>
          </li>)}</L10nValue></ul>}</L10nValue>
  </section>;
}
