"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState } from "react";
import type { PrescriptionLineFieldErrors, PrescriptionLineFormValue } from "./prescriptionFormValidation";
import type { DrugDTO } from "../../types";
import { formatDate, getDrugExpiryState } from "../../utils";
import { Pagination } from "@/components/ui/Pagination";
import { Button } from "@/components/ui/Button";
import { controlClassName } from "@/components/ui/Field";
import { DosageScheduleDialog } from "./DosageScheduleDialog";
import { formatDosageSchedule, suggestQuantity, type DosageSchedule } from "./dosageSchedule";

interface PrescriptionLinesEditorProps {
  lines: PrescriptionLineFormValue[];
  disabled?: boolean;
  listError?: string;
  errors: Record<string, PrescriptionLineFieldErrors>;
  drugOptions: DrugDTO[];
  drugKeyword: string;
  drugLoading: boolean;
  drugError: string | null;
  drugPage: number;
  drugPageInfo: { totalPages: number; totalElements: number };
  onDrugPageChange: (page: number) => void;
  onDrugKeywordChange: (value: string) => void;
  onSearchDrugs: () => void;
  onChange: (rowKey: string, patch: Partial<PrescriptionLineFormValue>) => void;
  /** Adds the drug as a new row and returns its key, or null when the form is locked. */
  onAdd: (drug: DrugDTO) => string | null;
  onRemove: (rowKey: string) => void;
}

function inputClass(invalid: boolean): string {
  return `${controlClassName}${invalid ? " border-danger" : ""}`;
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? <p id={id} role="alert" className="mt-1 text-xs text-danger"><L10nValue>{message}</L10nValue></p> : null;
}

function drugBlockReason(drug: DrugDTO, added: boolean): string | null {
  if (added) return "Đã thêm";
  if (drug.stockQuantity === 0) return "Hết hàng";
  return getDrugExpiryState(drug.expiryDate) === "EXPIRED" ? "Hết hạn" : null;
}

export function PrescriptionLinesEditor({
  lines,
  disabled = false,
  listError,
  errors,
  drugOptions,
  drugKeyword,
  drugLoading,
  drugError,
  drugPage,
  drugPageInfo,
  onDrugPageChange,
  onDrugKeywordChange,
  onSearchDrugs,
  onChange,
  onAdd,
  onRemove,
}: PrescriptionLinesEditorProps) {
  const { localize } = useLocale();
  const [editing, setEditing] = useState<{ rowKey: string; isNew: boolean } | null>(null);
  const focusFrame = useRef<number | null>(null);
  useEffect(() => () => {
    if (focusFrame.current !== null) cancelAnimationFrame(focusFrame.current);
  }, []);
  const selectedDrugIds = new Set(lines.map(line => line.drugId.toLowerCase()).filter(Boolean));
  const editingLine = editing ? lines.find(line => line.rowKey === editing.rowKey) ?? null : null;

  function closeDialog() {
    const closed = editing;
    setEditing(null);
    if (!closed) return;
    // Next drug after a new row; back to the opener after an edit.
    const target = closed.isNew ? "prescription-drug-keyword" : `dosage-${closed.rowKey}`;
    if (focusFrame.current !== null) cancelAnimationFrame(focusFrame.current);
    focusFrame.current = requestAnimationFrame(() => {
      focusFrame.current = null;
      // A fast validation reply or a user action may already have moved focus after the dialog closed.
      if (document.activeElement === document.body) document.getElementById(target)?.focus();
    });
  }

  function applySchedule(line: PrescriptionLineFormValue, schedule: DosageSchedule) {
    const unit = line.drug?.unit ?? "";
    const previous = line.schedule ? suggestQuantity(line.schedule, unit) : null;
    const next = suggestQuantity(schedule, unit);
    // A manual quantity survives reopening; only a changed calculation replaces it.
    onChange(line.rowKey, {
      schedule,
      dosage: formatDosageSchedule(schedule, unit),
      ...(next !== null && next !== previous ? { quantity: String(next) } : {}),
    });
    closeDialog();
  }

  function addDrug(drug: DrugDTO) {
    const rowKey = onAdd(drug);
    if (rowKey) setEditing({ rowKey, isNew: true });
  }

  return (
    <section className="min-w-0 space-y-4 rounded-xl border border-border bg-surface p-5 sm:p-6" aria-labelledby="prescription-lines-title">
      <div>
        <h2 id="prescription-lines-title" className="text-base font-semibold"><L10n>{"Toa thuốc"}</L10n></h2>
        <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Tìm thuốc rồi chọn để thêm vào toa, sau đó chọn cách dùng."}</L10n></p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="prescription-drug-keyword" className="mb-1 block text-sm font-medium"><L10n>{"Tìm thuốc trong danh mục"}</L10n></label>
          <input
            id="prescription-drug-keyword"
            disabled={disabled}
            value={drugKeyword}
            onChange={(event) => onDrugKeywordChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                onSearchDrugs();
              }
            }}
            placeholder={localize("Tìm theo tên thuốc hoặc hoạt chất")}
            className={inputClass(false)}
          />
        </div>
        <Button type="button" onClick={onSearchDrugs} disabled={disabled} loading={drugLoading} variant="primary"><L10n>{"Tìm thuốc"}</L10n></Button>
      </div>

      <L10nValue>{drugError && (
        <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
          <L10nValue>{drugError}</L10nValue>
        </p>
      )}</L10nValue>
      <L10nValue>{drugOptions.length === 0 && !drugLoading && !drugError && (
        <p className="text-sm text-muted-foreground"><L10n>{"Không có thuốc phù hợp. Thử đổi tên thuốc hoặc hoạt chất."}</L10n></p>
      )}</L10nValue>

      <L10nValue>{drugOptions.length > 0 && (
        <ul aria-label={localize("Kết quả tìm thuốc")} className="max-h-56 divide-y divide-border overflow-y-auto rounded-lg border border-border">
          <L10nValue>{drugOptions.map(drug => {
            const blocked = drugBlockReason(drug, selectedDrugIds.has(drug.drugId.toLowerCase()));
            return (
              <li key={drug.drugId} className="flex items-center gap-3 px-3 py-2 text-sm">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{drug.drugName}</p>
                  <p className="text-xs text-muted-foreground">
                    <L10nValue>{[drug.activeIngredient, drug.unit, `tồn ${drug.stockQuantity}`].filter(Boolean).join(" · ")}</L10nValue>
                    <L10nValue>{getDrugExpiryState(drug.expiryDate) === "EXPIRING_SOON" && ` · Sắp hết hạn ${formatDate(drug.expiryDate)}`}</L10nValue>
                  </p>
                </div>
                <L10nValue>{blocked
                  ? <span className="text-xs text-muted-foreground"><L10nValue>{blocked}</L10nValue></span>
                  : <Button type="button" disabled={disabled} aria-label={localize(`Thêm ${drug.drugName} vào toa`)} onClick={() => addDrug(drug)}><L10n>{"Thêm"}</L10n></Button>}</L10nValue>
              </li>
            );
          })}</L10nValue>
        </ul>
      )}</L10nValue>

      <L10nValue>{!drugError && drugPageInfo.totalPages > 1 && <Pagination page={drugPage}
        totalPages={drugPageInfo.totalPages} totalElements={drugPageInfo.totalElements}
        loading={disabled || drugLoading} onPageChange={onDrugPageChange} />}</L10nValue>
      <p className="text-xs text-muted-foreground"><L10n>{"Tồn hiện có có thể gồm số lượng đang giữ cho đơn khác; hệ thống kiểm tra lại khi tạo đơn."}</L10n></p>

      <L10nValue>{listError && <p role="alert" className="text-sm text-danger"><L10nValue>{listError}</L10nValue></p>}</L10nValue>

      <L10nValue>{lines.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border p-4 text-center text-sm text-muted-foreground"><L10n>{"Toa chưa có thuốc."}</L10n></p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="w-full min-w-[720px] table-fixed text-left text-sm">
            <caption className="sr-only"><L10n>{"Các thuốc trong toa"}</L10n></caption>
            <thead className="border-b border-border bg-surface-muted text-xs text-muted-foreground">
              <tr>
                <th scope="col" className="w-10 px-3 py-2">STT</th>
                <th scope="col" className="w-40 px-3 py-2"><L10n>{"Thuốc"}</L10n></th>
                <th scope="col" className="w-24 px-3 py-2"><L10n>{"Số lượng"}</L10n></th>
                <th scope="col" className="px-3 py-2"><L10n>{"Cách dùng"}</L10n></th>
                <th scope="col" className="w-36 px-3 py-2"><L10n>{"Ghi chú"}</L10n></th>
                <th scope="col" aria-label={localize("Thao tác")} className="w-14 px-3 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              <L10nValue>{lines.map((line, index) => {
                const lineErrors = errors[line.rowKey] ?? {};
                const row = index + 1;
                const quantity = Number(line.quantity);
                const overStock = line.drug !== null && Number.isFinite(quantity) && quantity > line.drug.stockQuantity;
                return (
                  <tr key={line.rowKey} className="align-top">
                    <td className="px-3 py-2 tabular-nums"><L10nValue>{row}</L10nValue></td>
                    <td className="px-3 py-2">
                      <div id={`drugId-${line.rowKey}`} tabIndex={-1} className="font-medium"><L10nValue>{line.drug?.drugName ?? "Thuốc chưa chọn"}</L10nValue></div>
                      <L10nValue>{line.drug && <p className="text-xs text-muted-foreground">{line.drug.unit}</p>}</L10nValue>
                      <FieldError id={`drugId-${line.rowKey}-error`} message={localize(lineErrors.drugId)} />
                    </td>
                    <td className="px-3 py-2">
                      <input
                        id={`quantity-${line.rowKey}`}
                        disabled={disabled}
                        aria-label={localize(`Số lượng dòng ${row}`)}
                        aria-required="true"
                        type="number"
                        inputMode="numeric"
                        min="1"
                        step="1"
                        value={line.quantity}
                        onChange={(event) => onChange(line.rowKey, { quantity: event.target.value })}
                        aria-invalid={lineErrors.quantity ? true : undefined}
                        aria-describedby={lineErrors.quantity ? `quantity-${line.rowKey}-error` : undefined}
                        className={inputClass(Boolean(lineErrors.quantity))}
                      />
                      <FieldError id={`quantity-${line.rowKey}-error`} message={localize(lineErrors.quantity)} />
                      <L10nValue>{overStock && !lineErrors.quantity && <p className="mt-1 text-xs text-danger"><L10n>{"Vượt tồn "}</L10n>{line.drug?.stockQuantity}</p>}</L10nValue>
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex items-start gap-2">
                        <textarea
                          id={`dosage-${line.rowKey}`}
                          readOnly
                          disabled={disabled}
                          rows={Math.max(2, Math.ceil(line.dosage.length / 22))}
                          aria-label={localize(`Cách dùng dòng ${row}, chỉ chọn qua hộp thoại`)}
                          aria-required="true"
                          value={line.dosage}
                          placeholder={localize("Chưa chọn cách dùng")}
                          onClick={() => setEditing({ rowKey: line.rowKey, isNew: false })}
                          onKeyDown={(event) => {
                            if (event.key === "Enter" || event.key === " ") {
                              event.preventDefault();
                              setEditing({ rowKey: line.rowKey, isNew: false });
                            }
                          }}
                          aria-invalid={lineErrors.dosage ? true : undefined}
                          aria-describedby={lineErrors.dosage ? `dosage-${line.rowKey}-error` : undefined}
                          className={`${inputClass(Boolean(lineErrors.dosage))} min-w-0 flex-1 cursor-pointer resize-none bg-surface-muted`}
                        />
                        <Button type="button" disabled={disabled} aria-label={localize(`Chọn cách dùng dòng ${row}`)} className="shrink-0 !px-3" onClick={() => setEditing({ rowKey: line.rowKey, isNew: false })}><L10n>{"Sửa"}</L10n></Button>
                      </div>
                      <FieldError id={`dosage-${line.rowKey}-error`} message={localize(lineErrors.dosage)} />
                    </td>
                    <td className="px-3 py-2">
                      <input
                        id={`note-${line.rowKey}`}
                        disabled={disabled}
                        aria-label={localize(`Ghi chú dòng ${row}`)}
                        value={line.note}
                        maxLength={500}
                        placeholder={localize("Thêm ghi chú")}
                        onChange={(event) => onChange(line.rowKey, { note: event.target.value })}
                        aria-invalid={lineErrors.note ? true : undefined}
                        aria-describedby={lineErrors.note ? `note-${line.rowKey}-error` : undefined}
                        className={inputClass(Boolean(lineErrors.note))}
                      />
                      <FieldError id={`note-${line.rowKey}-error`} message={localize(lineErrors.note)} />
                    </td>
                    <td className="px-3 py-2 text-right">
                      <Button type="button" onClick={() => onRemove(line.rowKey)} disabled={disabled} aria-label={localize(`Xóa dòng thuốc ${row}`)} variant="ghost" className="!px-2 text-danger"><L10n>{"Xóa"}</L10n></Button>
                    </td>
                  </tr>
                );
              })}</L10nValue>
            </tbody>
          </table>
        </div>
      )}</L10nValue>

      <L10nValue>{editingLine?.drug && (
        <DosageScheduleDialog
          key={editingLine.rowKey}
          drugName={editingLine.drug.drugName}
          unit={editingLine.drug.unit}
          stock={editingLine.drug.stockQuantity}
          initial={editingLine.schedule}
          onApply={schedule => applySchedule(editingLine, schedule)}
          onClose={closeDialog}
        />
      )}</L10nValue>
    </section>
  );
}
