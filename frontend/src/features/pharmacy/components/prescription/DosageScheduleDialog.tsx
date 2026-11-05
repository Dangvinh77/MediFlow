"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Button } from "@/components/ui/Button";
import { controlClassName } from "@/components/ui/Field";
import {
  DOSE_PRESETS,
  DOSE_STEP,
  DOSE_TIMINGS,
  MAX_DAYS,
  MAX_DOSE,
  applyDosePreset,
  createDosageSchedule,
  formatDosageSchedule,
  formatDose,
  suggestQuantity,
  validateDosageSchedule,
  type DosageSchedule,
  type DoseRow,
  type DoseTiming,
} from "./dosageSchedule";

interface DosageScheduleDialogProps {
  drugName: string;
  unit: string;
  stock: number;
  initial: DosageSchedule | null;
  onApply: (schedule: DosageSchedule) => void;
  onClose: () => void;
}

const FOCUSABLE = "button:not([disabled]), input:not([disabled]), select:not([disabled])";

/** Portaled out of the prescription form so Enter here can never submit the prescription. */
export function DosageScheduleDialog({ drugName, unit, stock, initial, onApply, onClose }: DosageScheduleDialogProps) {
  const [schedule, setSchedule] = useState<DosageSchedule>(() => initial ?? createDosageSchedule());
  const [error, setError] = useState<string | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    panelRef.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus();
  }, []);

  function update(next: DosageSchedule) {
    setSchedule(next);
    setError(null);
  }

  function updateRow(session: DoseRow["session"], patch: Partial<DoseRow>) {
    update({ ...schedule, rows: schedule.rows.map(row => (row.session === session ? { ...row, ...patch } : row)) });
  }

  function apply() {
    const problem = validateDosageSchedule(schedule);
    if (problem) {
      setError(problem);
      return;
    }
    onApply(schedule);
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.stopPropagation();
      onClose();
      return;
    }
    if (event.key !== "Tab") return;
    const items = [...(panelRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])];
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }

  const valid = schedule.rows.some(row => row.selected) && validateDosageSchedule(schedule) === null;
  const preview = valid ? formatDosageSchedule(schedule, unit) : "";
  const suggestion = suggestQuantity(schedule, unit);

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 sm:items-center sm:p-4">
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dosage-dialog-title"
        onKeyDown={handleKeyDown}
        className="max-h-[92dvh] w-full max-w-2xl overflow-y-auto rounded-t-xl border border-border bg-surface p-4 sm:rounded-xl sm:p-5"
      >
        <h2 id="dosage-dialog-title" className="text-base font-semibold">Cách dùng: {drugName}</h2>

        <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="Mẫu nhanh">
          {DOSE_PRESETS.map(preset => (
            <button
              key={preset.label}
              type="button"
              onClick={() => update(applyDosePreset(schedule, preset.sessions))}
              className="min-h-9 rounded-lg border border-control-border px-3 text-sm hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              {preset.label}
            </button>
          ))}
        </div>

        <div className="mt-3 overflow-x-auto rounded-lg border border-border">
          <table className="w-full min-w-[480px] text-left text-sm">
            <caption className="sr-only">Buổi, thời điểm dùng và liều lượng</caption>
            <thead className="border-b border-border bg-surface-muted text-xs text-muted-foreground">
              <tr>
                <th scope="col" className="px-3 py-2">Buổi</th>
                <th scope="col" className="px-3 py-2">Thời điểm dùng</th>
                <th scope="col" className="px-3 py-2">Liều lượng</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {schedule.rows.map(row => (
                <tr key={row.session} className={row.selected ? "" : "text-muted-foreground"}>
                  <th scope="row" className="px-3 py-2 font-medium">
                    <label className="flex min-h-9 cursor-pointer items-center gap-2">
                      <input
                        type="checkbox"
                        checked={row.selected}
                        onChange={event => updateRow(row.session, { selected: event.target.checked })}
                        className="size-4"
                      />
                      {row.session}
                    </label>
                  </th>
                  <td className="px-3 py-2">
                    {row.selected ? (
                      <select
                        aria-label={`Thời điểm dùng buổi ${row.session}`}
                        value={row.timing}
                        onChange={event => updateRow(row.session, { timing: event.target.value as DoseTiming })}
                        className={controlClassName}
                      >
                        {DOSE_TIMINGS.map(timing => <option key={timing} value={timing}>{timing}</option>)}
                      </select>
                    ) : "—"}
                  </td>
                  <td className="px-3 py-2">
                    {row.selected ? (
                      <div className="flex items-center gap-1">
                        <button
                          type="button"
                          aria-label={`Giảm liều buổi ${row.session}`}
                          disabled={row.dose <= DOSE_STEP}
                          onClick={() => updateRow(row.session, { dose: row.dose - DOSE_STEP })}
                          className="size-9 rounded-lg border border-control-border hover:bg-surface-muted disabled:opacity-55"
                        >−</button>
                        <span aria-live="polite" aria-label={`Liều buổi ${row.session}`} className="min-w-9 text-center font-medium tabular-nums">{formatDose(row.dose)}</span>
                        <button
                          type="button"
                          aria-label={`Tăng liều buổi ${row.session}`}
                          disabled={row.dose >= MAX_DOSE}
                          onClick={() => updateRow(row.session, { dose: row.dose + DOSE_STEP })}
                          className="size-9 rounded-lg border border-control-border hover:bg-surface-muted disabled:opacity-55"
                        >+</button>
                        <span className="ml-1 text-muted-foreground">{unit}</span>
                      </div>
                    ) : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <label htmlFor="dosage-days" className="font-medium">Số ngày dùng</label>
          <input
            id="dosage-days"
            type="number"
            inputMode="numeric"
            min="1"
            max={MAX_DAYS}
            step="1"
            value={schedule.days}
            onChange={event => update({ ...schedule, days: event.target.value })}
            className={`${controlClassName} !w-24`}
          />
          {suggestion !== null && (
            <span className={suggestion > stock ? "text-danger" : "text-muted-foreground"}>
              Gợi ý số lượng: {suggestion} {unit}{suggestion > stock ? ` (vượt tồn ${stock})` : ""}
            </span>
          )}
        </div>

        <p className="mt-3 text-xs font-medium text-muted-foreground">Cách dùng sẽ ghi vào toa</p>
        <p aria-live="polite" className="mt-1 min-h-10 rounded-lg border border-border bg-surface-muted px-3 py-2 text-sm">
          {preview || "Chọn ít nhất một buổi dùng."}
        </p>

        {error && <p role="alert" className="mt-2 text-sm text-danger">{error}</p>}

        <div className="mt-4 flex justify-end gap-2">
          <Button type="button" onClick={onClose}>Hủy</Button>
          <Button type="button" variant="primary" onClick={apply}>Áp dụng</Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
