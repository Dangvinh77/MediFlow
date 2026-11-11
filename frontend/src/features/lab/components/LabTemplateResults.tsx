import { Field, controlClassName } from "@/components/ui/Field";
import type { LabTemplateFieldDTO } from "../types";

export interface LabTemplateDraft {
  value: string;
  reason: string;
  operator: string;
}

export type LabTemplateDrafts = Record<string, LabTemplateDraft>;

const NO_RESULT_PREFIX = "Không có kết quả: ";

export function selectedRange(field: LabTemplateFieldDTO) {
  return field.ranges.find((range) => range.sex === null && range.ageMinDays === null && range.ageMaxDays === null)
    ?? field.ranges[0]
    ?? null;
}

function rangeText(field: LabTemplateFieldDTO) {
  const range = selectedRange(field);
  if (!range) return null;
  const bound = (value: number | string | null) => value === null ? "—" : String(value);
  return `${bound(range.low)}–${bound(range.high)}`;
}

export function templateDemoRangeIsShown(fields: LabTemplateFieldDTO[]) {
  return fields.some((field) => {
    const range = selectedRange(field);
    return range !== null && !range.reviewed;
  });
}

export function mapTemplateResults(fields: LabTemplateFieldDTO[], drafts: LabTemplateDrafts) {
  const rows: { indicator: string; value: string; unit: string | null; referenceRange: string | null }[] = [];
  for (const field of [...fields].sort((left, right) => left.position - right.position)) {
    const draft = drafts[field.fieldCode] ?? { value: "", reason: "", operator: "" };
    const value = draft.value.trim();
    const reason = draft.reason.trim();
    if (value && reason) return { rows: [], error: `Chỉ nhập kết quả hoặc lý do không có kết quả cho ${field.label}.` };
    if (field.required && !value && !reason) {
      return { rows: [], error: `Vui lòng nhập kết quả hoặc lý do không có kết quả cho ${field.label}.` };
    }
    if (!value && !reason) continue;
    const submittedValue = reason ? `${NO_RESULT_PREFIX}${reason}` : `${draft.operator}${value}`;
    const referenceRange = rangeText(field);
    if (field.label.length > 100 || submittedValue.length > 50 || (field.unit?.length ?? 0) > 20 || (referenceRange?.length ?? 0) > 50) {
      return { rows: [], error: `Dữ liệu của ${field.label} vượt quá giới hạn lưu kết quả.` };
    }
    rows.push({ indicator: field.label, value: submittedValue, unit: field.unit, referenceRange });
  }
  if (rows.length === 0) return { rows: [], error: "Nhập ít nhất một kết quả hoặc lý do không có kết quả." };
  return { rows, error: null };
}

export function LabTemplateResults({
  fields,
  drafts,
  onDraftChange,
}: {
  fields: LabTemplateFieldDTO[];
  drafts: LabTemplateDrafts;
  onDraftChange: (fieldCode: string, change: Partial<LabTemplateDraft>) => void;
}) {
  const orderedFields = [...fields].sort((left, right) => left.position - right.position);
  return <div className="space-y-4">
    {templateDemoRangeIsShown(orderedFields) ? <p className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning">Khoảng tham chiếu demo, chưa được duyệt lâm sàng</p> : null}
    {orderedFields.map((field) => {
      const draft = drafts[field.fieldCode] ?? { value: "", reason: "", operator: "" };
      const inputId = `lab-template-value-${field.fieldCode}`;
      const options = field.allowedOptions ?? [];
      const isChoice = (field.valueType === "QUALITATIVE" || field.valueType === "SEMI_QUANTITATIVE") && options.length > 0;
      return <div key={field.fieldCode} className="grid gap-3 rounded-lg border border-border p-4 md:grid-cols-2">
        <Field label={`Giá trị ${field.label}`} htmlFor={inputId} required={field.required}>
          {isChoice ? <select id={inputId} aria-label={`Giá trị ${field.label}`} value={draft.value} onChange={(event) => onDraftChange(field.fieldCode, { value: event.target.value })} className={controlClassName}>
            <option value="">Chọn kết quả</option>
            {options.map((option) => <option key={option} value={option}>{option}</option>)}
          </select> : field.valueType === "TEXT" ? <textarea id={inputId} aria-label={`Giá trị ${field.label}`} rows={2} maxLength={field.allowsOperator ? 48 : 50} value={draft.value} onChange={(event) => onDraftChange(field.fieldCode, { value: event.target.value })} className={controlClassName} /> : <input id={inputId} aria-label={`Giá trị ${field.label}`} type={field.valueType === "QUANTITATIVE" ? "number" : "text"} step={field.valueType === "QUANTITATIVE" ? "any" : undefined} maxLength={field.allowsOperator ? 48 : 50} value={draft.value} onChange={(event) => onDraftChange(field.fieldCode, { value: event.target.value })} className={controlClassName} />}
        </Field>
        {field.allowsOperator ? <Field label={`Toán tử ${field.label}`} htmlFor={`lab-template-operator-${field.fieldCode}`}>
          <select id={`lab-template-operator-${field.fieldCode}`} aria-label={`${field.label} toán tử`} value={draft.operator} onChange={(event) => onDraftChange(field.fieldCode, { operator: event.target.value })} className={controlClassName}>
            <option value="">Không có</option>
            {["<", "<=", ">", ">="].map((operator) => <option key={operator} value={operator}>{operator}</option>)}
          </select>
        </Field> : null}
        <Field label={`Đơn vị ${field.label}`} htmlFor={`lab-template-unit-${field.fieldCode}`}>
          <input id={`lab-template-unit-${field.fieldCode}`} aria-label={`Đơn vị ${field.label}`} readOnly value={field.unit ?? ""} className={controlClassName} />
        </Field>
        <Field label={`Khoảng tham chiếu ${field.label}`} htmlFor={`lab-template-range-${field.fieldCode}`}>
          <input id={`lab-template-range-${field.fieldCode}`} aria-label={`Khoảng tham chiếu ${field.label}`} readOnly value={rangeText(field) ?? ""} className={controlClassName} />
        </Field>
        <Field label={`Lý do không có kết quả ${field.label}`} htmlFor={`lab-template-reason-${field.fieldCode}`}>
          <input id={`lab-template-reason-${field.fieldCode}`} maxLength={50 - NO_RESULT_PREFIX.length} value={draft.reason} onChange={(event) => onDraftChange(field.fieldCode, { reason: event.target.value })} className={controlClassName} />
        </Field>
      </div>;
    })}
  </div>;
}
