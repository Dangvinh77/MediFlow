"use client";

import type { PrescriptionLineFieldErrors, PrescriptionLineFormValue } from "./prescriptionFormValidation";
import type { DrugDTO } from "../../types";
import { formatVnd } from "../../utils";

interface PrescriptionLinesEditorProps {
  lines: PrescriptionLineFormValue[];
  errors: Record<string, PrescriptionLineFieldErrors>;
  drugOptions: DrugDTO[];
  drugKeyword: string;
  drugLoading: boolean;
  drugError: string | null;
  onDrugKeywordChange: (value: string) => void;
  onSearchDrugs: () => void;
  onChange: (rowKey: string, patch: Partial<PrescriptionLineFormValue>) => void;
  onAdd: () => void;
  onRemove: (rowKey: string) => void;
}

function inputClass(invalid: boolean): string {
  return [
    "w-full rounded-lg border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground",
    invalid ? "border-danger" : "border-border",
  ].join(" ");
}

export function PrescriptionLinesEditor({
  lines,
  errors,
  drugOptions,
  drugKeyword,
  drugLoading,
  drugError,
  onDrugKeywordChange,
  onSearchDrugs,
  onChange,
  onAdd,
  onRemove,
}: PrescriptionLinesEditorProps) {
  const selectedDrugIds = new Set(
    lines.map((line) => line.drugId.toLowerCase()).filter(Boolean),
  );
  const availableDrugs = [...drugOptions];
  lines.forEach((line) => {
    if (line.drug && !availableDrugs.some((drug) => drug.drugId === line.drug?.drugId)) {
      availableDrugs.push(line.drug);
    }
  });

  return (
    <section className="space-y-4 rounded-xl border border-border bg-surface p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-base font-semibold">Dòng thuốc</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Giá và tổng tiền được backend chụp từ danh mục tại thời điểm tạo đơn.
          </p>
        </div>
        <button
          type="button"
          onClick={onAdd}
          className="rounded-lg border border-border px-3 py-2 text-sm font-medium hover:bg-surface-muted"
        >
          Thêm dòng thuốc
        </button>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row">
        <div className="min-w-0 flex-1">
          <label htmlFor="prescription-drug-keyword" className="mb-1 block text-sm font-medium">
            Tìm thuốc trong danh mục
          </label>
          <input
            id="prescription-drug-keyword"
            value={drugKeyword}
            onChange={(event) => onDrugKeywordChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                onSearchDrugs();
              }
            }}
            placeholder="Ví dụ: Paracetamol"
            className={inputClass(false)}
          />
        </div>
        <button
          type="button"
          onClick={onSearchDrugs}
          disabled={drugLoading}
          className="rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {drugLoading ? "Đang tìm…" : "Tìm thuốc"}
        </button>
      </div>

      {drugError && (
        <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
          {drugError}
        </p>
      )}
      {drugOptions.length === 0 && !drugLoading && !drugError && (
        <p className="text-sm text-muted-foreground">
          Chưa có thuốc trong lựa chọn. Hãy tìm theo tên thuốc.
        </p>
      )}

      <div className="space-y-4">
        {lines.map((line, index) => {
          const lineErrors = errors[line.rowKey] ?? {};
          const selectedDrug = line.drug;

          return (
            <div key={line.rowKey} className="rounded-lg border border-border p-4">
              <div className="mb-3 flex items-center justify-between gap-3">
                <h3 className="font-medium">Thuốc {index + 1}</h3>
                <button
                  type="button"
                  onClick={() => onRemove(line.rowKey)}
                  disabled={lines.length === 1}
                  className="text-sm font-medium text-danger hover:underline disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Xóa dòng
                </button>
              </div>

              <div className="grid gap-4 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,2fr)]">
                <div className="flex flex-col gap-1">
                  <label htmlFor={`drug-${line.rowKey}`} className="text-sm font-medium">
                    Thuốc <span aria-hidden="true">*</span>
                  </label>
                  <select
                    id={`drug-${line.rowKey}`}
                    value={line.drugId}
                    onChange={(event) => {
                      const drug = drugOptions.find((option) => option.drugId === event.target.value) ?? null;
                      onChange(line.rowKey, { drugId: event.target.value, drug });
                    }}
                    aria-invalid={lineErrors.drugId ? true : undefined}
                    aria-describedby={lineErrors.drugId ? `drug-${line.rowKey}-error` : undefined}
                    className={inputClass(Boolean(lineErrors.drugId))}
                  >
                    <option value="">Chọn thuốc</option>
                    {availableDrugs.map((drug) => (
                      <option
                        key={drug.drugId}
                        value={drug.drugId}
                        disabled={selectedDrugIds.has(drug.drugId.toLowerCase()) && drug.drugId !== line.drugId}
                      >
                        {drug.drugName} · {drug.unit} · {formatVnd(drug.price)} · tồn {drug.stockQuantity}
                      </option>
                    ))}
                  </select>
                  {selectedDrug && (
                    <p className="text-xs text-muted-foreground">
                      Tạm tính: {formatVnd(selectedDrug.price * (Number(line.quantity) || 0))} · không dùng làm nguồn giá gửi backend
                    </p>
                  )}
                  {lineErrors.drugId && <p id={`drug-${line.rowKey}-error`} className="text-xs text-danger">{lineErrors.drugId}</p>}
                </div>

                <div className="flex flex-col gap-1">
                  <label htmlFor={`quantity-${line.rowKey}`} className="text-sm font-medium">
                    Số lượng <span aria-hidden="true">*</span>
                  </label>
                  <input
                    id={`quantity-${line.rowKey}`}
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
                  {lineErrors.quantity && <p id={`quantity-${line.rowKey}-error`} className="text-xs text-danger">{lineErrors.quantity}</p>}
                </div>

                <div className="flex flex-col gap-1">
                  <label htmlFor={`dosage-${line.rowKey}`} className="text-sm font-medium">Cách dùng</label>
                  <input
                    id={`dosage-${line.rowKey}`}
                    value={line.dosage}
                    maxLength={255}
                    onChange={(event) => onChange(line.rowKey, { dosage: event.target.value })}
                    aria-invalid={lineErrors.dosage ? true : undefined}
                    aria-describedby={lineErrors.dosage ? `dosage-${line.rowKey}-error` : undefined}
                    className={inputClass(Boolean(lineErrors.dosage))}
                    placeholder="Ví dụ: 1 viên sau ăn"
                  />
                  {lineErrors.dosage && <p id={`dosage-${line.rowKey}-error`} className="text-xs text-danger">{lineErrors.dosage}</p>}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
