"use client";

import { useEffect, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { AsyncState } from "@/components/ui/AsyncState";
import { ReferenceText } from "@/components/references/ReferenceText";
import { appointmentApi } from "../api";
import { queueNumberText, registrationOutcomeLabel, roomNumberText } from "../presentation";
import type { ScheduleImpactDTO } from "../types";

interface ImpactRead { key: string; impact?: ScheduleImpactDTO; message?: string; correlationId?: string | null }

export function ScheduleImpactPanel({ kind, id }: { kind: "session" | "shift"; id: string }) {
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
  return <section aria-label="Ảnh hưởng của thay đổi lịch" className="space-y-3 rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
    <h3 className="font-semibold">Bệnh nhân bị ảnh hưởng</h3>
    {!current ? <AsyncState kind="loading" message="Đang tải danh sách bệnh nhân bị ảnh hưởng…" /> : null}
    {current?.message ? <AsyncState kind="error" message={current.message} correlationId={current.correlationId ?? null} onRetry={() => setAttempt(value => value + 1)} /> : null}
    {impact ? <>
      <dl className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Count label="Đang giữ chỗ" value={impact.heldCount} />
        <Count label="Đang thanh toán" value={impact.paymentLockedCount} />
        <Count label="Đã xác nhận" value={impact.confirmedCount} />
        <Count label="Đã vào khám" value={impact.consumedCount} />
      </dl>
      {impact.registrations.length ? <>
        <p>Hãy đổi lịch hoặc hủy từng phiếu dưới đây tại màn tiếp nhận trước khi thay đổi lịch này.</p>
        <ul className="divide-y divide-border rounded-lg border border-border bg-surface">{impact.registrations.map(item => {
          const room = roomNumberText(item.roomQueueNumber);
          return <li key={item.registrationId} className="flex flex-wrap items-center justify-between gap-2 p-3">
            <ReferenceText kind="patient" value={item.patientId} fallback="Bệnh nhân" />
            <span>{registrationOutcomeLabel(item)}</span>
            <span>{queueNumberText(item.queueNumber, true)}{room ? ` · ${room}` : ""}</span>
          </li>;
        })}</ul>
      </> : <p role="status">Không có phiếu nào bị ảnh hưởng.</p>}
    </> : null}
  </section>;
}

function Count({ label, value }: { label: string; value: number }) {
  return <div><dt className="text-muted-foreground">{label}</dt><dd className="font-semibold tabular-nums">{value}</dd></div>;
}
