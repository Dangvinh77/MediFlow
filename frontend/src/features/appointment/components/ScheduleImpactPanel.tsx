"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { AsyncState } from "@/components/ui/AsyncState";
import { ReferenceText } from "@/components/references/ReferenceText";
import { appointmentApi } from "../api";
import { queueNumberText, registrationOutcomeLabel, roomNumberText } from "../presentation";
import type { ScheduleImpactDTO } from "../types";

interface ImpactRead { key: string; impact?: ScheduleImpactDTO; message?: string; correlationId?: string | null }

export function ScheduleImpactPanel({ kind, id }: { kind: "session" | "shift"; id: string }) {
  const { localize } = useLocale();
  const [attempt, setAttempt] = useState(0);
  const [read, setRead] = useState<ImpactRead | null>(null);
  const key = `${kind}:${id}:${attempt}`;
  useEffect(() => {
    let live = true;
    (kind === "session" ? appointmentApi.sessionImpact(id) : appointmentApi.shiftImpact(id))
      .then(impact => { if (live) setRead({ key, impact }); })
      .catch((cause: unknown) => {
        if (live) setRead({ key, message: cause instanceof ApiRequestError ? cause.message : "Không thể tải danh sách bệnh nhân bị ảnh hưởng.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
      });
    return () => { live = false; };
  }, [kind, id, key]);
  const current = read?.key === key ? read : null;
  const impact = current?.impact;
  return <section aria-label={localize("Ảnh hưởng của thay đổi lịch")} className="space-y-3 rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
    <h3 className="font-semibold"><L10n>{"Bệnh nhân bị ảnh hưởng"}</L10n></h3>
    <L10nValue>{!current ? <AsyncState kind="loading" message={localize("Đang tải danh sách bệnh nhân bị ảnh hưởng…")} /> : null}</L10nValue>
    <L10nValue>{current?.message ? <AsyncState kind="error" message={localize(current.message)} correlationId={current.correlationId ?? null} onRetry={() => setAttempt(value => value + 1)} /> : null}</L10nValue>
    <L10nValue>{impact ? <>
      <dl className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Count label={localize("Đang giữ chỗ")} value={impact.heldCount} />
        <Count label={localize("Đang thanh toán")} value={impact.paymentLockedCount} />
        <Count label={localize("Đã xác nhận")} value={impact.confirmedCount} />
        <Count label={localize("Đã vào khám")} value={impact.consumedCount} />
      </dl>
      <L10nValue>{impact.registrations.length ? <>
        <p><L10n>{"Hãy đổi lịch hoặc hủy từng phiếu dưới đây tại màn tiếp nhận trước khi thay đổi lịch này."}</L10n></p>
        <ul className="divide-y divide-border rounded-lg border border-border bg-surface"><L10nValue>{impact.registrations.map(item => {
          const room = roomNumberText(item.roomQueueNumber);
          return <li key={item.registrationId} className="flex flex-wrap items-center justify-between gap-2 p-3">
            <ReferenceText kind="patient" value={item.patientId} fallback="Bệnh nhân" />
            <span><L10nValue>{registrationOutcomeLabel(item)}</L10nValue></span>
            <span><L10nValue>{queueNumberText(item.queueNumber, true)}</L10nValue><L10nValue>{room ? ` · ${room}` : ""}</L10nValue></span>
          </li>;
        })}</L10nValue></ul>
      </> : <p role="status"><L10n>{"Không có phiếu nào bị ảnh hưởng."}</L10n></p>}</L10nValue>
    </> : null}</L10nValue>
  </section>;
}

function Count({ label, value }: { label: string; value: number }) {
  return <div><dt className="text-muted-foreground"><L10nValue>{label}</L10nValue></dt><dd className="font-semibold tabular-nums">{value}</dd></div>;
}
