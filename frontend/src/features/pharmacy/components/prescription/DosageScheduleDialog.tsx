"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


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
  const { localize } = useLocale();
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
        <h2 id="dosage-dialog-title" className="text-base font-semibold"><L10n>{"Cách dùng: "}</L10n><L10nValue>{drugName}</L10nValue></h2>

        <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label={localize("Mẫu nhanh")}>
          <L10nValue>{DOSE_PRESETS.map(preset => (
            <button
              key={preset.label}
              type="button"
              onClick={() => update(applyDosePreset(schedule, preset.sessions))}
              className="min-h-9 rounded-lg border border-control-border px-3 text-sm hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <L10nValue>{preset.label}</L10nValue>
            </button>
          ))}</L10nValue>
        </div>

        <div className="mt-3 overflow-x-auto rounded-lg border border-border">
          <table className="w-full min-w-[480px] text-left text-sm">
            <caption className="sr-only"><L10n>{"Buổi, thời điểm dùng và liều lượng"}</L10n></caption>
            <thead className="border-b border-border bg-surface-muted text-xs text-muted-foreground">
              <tr>
                <th scope="col" className="px-3 py-2"><L10n>{"Buổi"}</L10n></th>
                <th scope="col" className="px-3 py-2"><L10n>{"Thời điểm dùng"}</L10n></th>
                <th scope="col" className="px-3 py-2"><L10n>{"Liều lượng"}</L10n></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              <L10nValue>{schedule.rows.map(row => (
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
                    <L10nValue>{row.selected ? (
                      <select
                        aria-label={localize(`Thời điểm dùng buổi ${row.session}`)}
                        value={row.timing}
                        onChange={event => updateRow(row.session, { timing: event.target.value as DoseTiming })}
                        className={controlClassName}
                      >
                        <L10nValue>{DOSE_TIMINGS.map(timing => <option key={timing} value={timing}><L10nValue>{timing}</L10nValue></option>)}</L10nValue>
                      </select>
                    ) : "—"}</L10nValue>
                  </td>
                  <td className="px-3 py-2">
                    <L10nValue>{row.selected ? (
                      <div className="flex items-center gap-1">
                        <button
                          type="button"
                          aria-label={localize(`Giảm liều buổi ${row.session}`)}
                          disabled={row.dose <= DOSE_STEP}
                          onClick={() => updateRow(row.session, { dose: row.dose - DOSE_STEP })}
                          className="size-9 rounded-lg border border-control-border hover:bg-surface-muted disabled:opacity-55"
                        >−</button>
                        <span aria-live="polite" aria-label={localize(`Liều buổi ${row.session}`)} className="min-w-9 text-center font-medium tabular-nums"><L10nValue>{formatDose(row.dose)}</L10nValue></span>
                        <button
                          type="button"
                          aria-label={localize(`Tăng liều buổi ${row.session}`)}
                          disabled={row.dose >= MAX_DOSE}
                          onClick={() => updateRow(row.session, { dose: row.dose + DOSE_STEP })}
                          className="size-9 rounded-lg border border-control-border hover:bg-surface-muted disabled:opacity-55"
                        >+</button>
                        <span className="ml-1 text-muted-foreground"><L10nValue>{unit}</L10nValue></span>
                      </div>
                    ) : "—"}</L10nValue>
                  </td>
                </tr>
              ))}</L10nValue>
            </tbody>
          </table>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <label htmlFor="dosage-days" className="font-medium"><L10n>{"Số ngày dùng"}</L10n></label>
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
          <L10nValue>{suggestion !== null && (
            <span className={suggestion > stock ? "text-danger" : "text-muted-foreground"}><L10n>{"Gợi ý số lượng: "}</L10n><L10nValue>{suggestion}</L10nValue> <L10nValue>{unit}</L10nValue><L10nValue>{suggestion > stock ? ` (vượt tồn ${stock})` : ""}</L10nValue>
            </span>
          )}</L10nValue>
        </div>

        <p className="mt-3 text-xs font-medium text-muted-foreground"><L10n>{"Cách dùng sẽ ghi vào toa"}</L10n></p>
        <p aria-live="polite" className="mt-1 min-h-10 rounded-lg border border-border bg-surface-muted px-3 py-2 text-sm">
          <L10nValue>{preview || "Chọn ít nhất một buổi dùng."}</L10nValue>
        </p>

        <L10nValue>{error && <p role="alert" className="mt-2 text-sm text-danger"><L10nValue>{error}</L10nValue></p>}</L10nValue>

        <div className="mt-4 flex justify-end gap-2">
          <Button type="button" onClick={onClose}><L10n>{"Hủy"}</L10n></Button>
          <Button type="button" variant="primary" onClick={apply}><L10n>{"Áp dụng"}</L10n></Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
