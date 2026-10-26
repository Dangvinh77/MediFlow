"use client";

import type { PrescriptionLineFieldErrors, PrescriptionLineFormValue } from "./prescriptionFormValidation";
import type { DrugDTO } from "../../types";
import { formatDate, formatVnd, getDrugExpiryState } from "../../utils";
import { Pagination } from "@/components/ui/Pagination";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";

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
  onAdd: () => void;
  onRemove: (rowKey: string) => void;
}

function inputClass(invalid: boolean): string {
  return `${controlClassName}${invalid ? " border-danger" : ""}`;
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
    <section className="min-w-0 space-y-5 rounded-xl border border-border bg-surface p-5 sm:p-6" aria-labelledby="prescription-lines-title">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 id="prescription-lines-title" className="text-base font-semibold">Dòng thuốc</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Chọn thuốc, số lượng và cách dùng. Giá tham khảo có thể thay đổi khi tạo đơn.
          </p>
        </div>
        <Button
          id="prescription-add-line"
          type="button"
          disabled={disabled}
          onClick={onAdd}
        >
          Thêm dòng thuốc
        </Button>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="prescription-drug-keyword" className="mb-1 block text-sm font-medium">
            Tìm thuốc trong danh mục
          </label>
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
            placeholder="Tìm theo tên thuốc hoặc hoạt chất"
            className={inputClass(false)}
          />
        </div>
        <Button
          type="button"
          onClick={onSearchDrugs}
          disabled={disabled}
          loading={drugLoading}
          variant="primary"
        >
          Tìm thuốc
        </Button>
      </div>

      {drugError && (
        <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
          {drugError}
        </p>
      )}
      {listError && <p role="alert" className="text-sm text-danger">{listError}</p>}
      {drugOptions.length === 0 && !drugLoading && !drugError && (
        <p className="text-sm text-muted-foreground">
          Không có thuốc phù hợp. Thử đổi tên thuốc hoặc hoạt chất.
        </p>
      )}

      {!drugError && drugPageInfo.totalPages > 1 && <Pagination page={drugPage}
        totalPages={drugPageInfo.totalPages} totalElements={drugPageInfo.totalElements}
        loading={disabled || drugLoading} onPageChange={onDrugPageChange} />}
      <p className="text-xs text-muted-foreground">Thuốc đã chọn được giữ khi đổi trang tìm kiếm. Tồn hiện có có thể gồm số lượng đang giữ cho đơn khác; hệ thống kiểm tra lại khi tạo đơn.</p>

      <div className="space-y-4">
        {lines.map((line, index) => {
          const lineErrors = errors[line.rowKey] ?? {};
          const selectedDrug = line.drug;

          return (
            <section key={line.rowKey} className="min-w-0 rounded-lg border border-border p-4" aria-labelledby={`line-title-${line.rowKey}`}>
              <div className="mb-3 flex items-center justify-between gap-3">
                <h3 id={`line-title-${line.rowKey}`} className="font-medium">Thuốc {index + 1}</h3>
                <Button
                  type="button"
                  onClick={() => onRemove(line.rowKey)}
                  disabled={disabled || lines.length === 1}
                  aria-label={`Xóa dòng thuốc ${index + 1}`}
                  variant="ghost"
                  className="text-danger"
                >
                  Xóa dòng
                </Button>
              </div>

              <div className="grid gap-4 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,2fr)]">
                <Field label="Thuốc" htmlFor={`drugId-${line.rowKey}`} error={lineErrors.drugId} required>
                  <select
                    id={`drugId-${line.rowKey}`}
                    disabled={disabled}
                    aria-required="true"
                    value={line.drugId}
                    onChange={(event) => {
                      const drug = availableDrugs.find((option) => option.drugId === event.target.value) ?? null;
                      onChange(line.rowKey, { drugId: event.target.value, drug });
                    }}
                    aria-invalid={lineErrors.drugId ? true : undefined}
                    aria-describedby={lineErrors.drugId ? `drugId-${line.rowKey}-error` : undefined}
                    className={inputClass(Boolean(lineErrors.drugId))}
                  >
                    <option value="">Chọn thuốc</option>
                    {availableDrugs.map((drug) => (
                      <option
                        key={drug.drugId}
                        value={drug.drugId}
                        disabled={drug.drugId !== line.drugId && (selectedDrugIds.has(drug.drugId.toLowerCase())
                          || drug.stockQuantity === 0 || getDrugExpiryState(drug.expiryDate) === "EXPIRED")}
                      >
                        {drug.drugName} · {drug.unit} · {formatVnd(drug.price)} · tồn {drug.stockQuantity}{getDrugExpiryState(drug.expiryDate) === "EXPIRED" ? " · Hết hạn" : ""}
                      </option>
                    ))}
                  </select>
                  {selectedDrug && (
                    <p className="text-xs text-muted-foreground">
                      Giá tham khảo: {formatVnd(selectedDrug.price)} / {selectedDrug.unit} · tồn {selectedDrug.stockQuantity}
                      {` · Hạn dùng: ${formatDate(selectedDrug.expiryDate)}`}
                      {getDrugExpiryState(selectedDrug.expiryDate) === "EXPIRING_SOON" && " · Sắp hết hạn"}
                    </p>
                  )}
                </Field>

                <Field label="Số lượng" htmlFor={`quantity-${line.rowKey}`} error={lineErrors.quantity} required>
                  <input
                    id={`quantity-${line.rowKey}`}
                    disabled={disabled}
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
                </Field>

                <Field label="Cách dùng" htmlFor={`dosage-${line.rowKey}`} error={lineErrors.dosage}>
                  <input
                    id={`dosage-${line.rowKey}`}
                    disabled={disabled}
                    value={line.dosage}
                    maxLength={255}
                    onChange={(event) => onChange(line.rowKey, { dosage: event.target.value })}
                    aria-invalid={lineErrors.dosage ? true : undefined}
                    aria-describedby={lineErrors.dosage ? `dosage-${line.rowKey}-error` : undefined}
                    className={inputClass(Boolean(lineErrors.dosage))}
                    placeholder="Ví dụ: 1 viên sau ăn"
                  />
                </Field>
              </div>
            </section>
          );
        })}
      </div>
      <p className="border-t border-border pt-4 text-sm">Tổng tiền tham khảo: <strong>{formatVnd(lines.reduce((sum, line) => {
        const quantity = Number(line.quantity);
        return sum + (line.drug && Number.isInteger(quantity) && quantity > 0 ? line.drug.price * quantity : 0);
      }, 0))}</strong>. Giá và số tiền chính thức được xác nhận khi tạo đơn thành công.</p>
    </section>
  );
}
