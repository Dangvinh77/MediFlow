"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import type { ClinicalContentDTO } from "../types";
import { inputClass } from "./DiagnosisFormFields";

export const clinicalContentSections = [
  ["chiefComplaint", "Lý do khám chính"],
  ["presentIllness", "Bệnh sử"],
  ["pastHistory", "Tiền sử"],
  ["allergyNote", "Ghi chú dị ứng của lượt khám"],
  ["physicalExam", "Khám thực thể"],
  ["advice", "Lời dặn"],
] as const satisfies ReadonlyArray<readonly [keyof ClinicalContentDTO, string]>;

export type ClinicalContentValues = Required<{ [K in (typeof clinicalContentSections)[number][0]]: string }>;

export function ClinicalContentFields({
  value,
  onChange,
  disabled = false,
}: {
  value: ClinicalContentValues;
  onChange: (field: keyof ClinicalContentValues, next: string) => void;
  disabled?: boolean;
}) {
  return (
    <fieldset disabled={disabled} className="space-y-4 border-t border-border pt-5">
      <legend className="text-lg font-semibold"><L10n>{"Nội dung khám có cấu trúc"}</L10n></legend>
      <L10nValue>{clinicalContentSections.map(([field, label]) => (
        <div key={field} className="flex flex-col gap-1">
          <label htmlFor={`clinical-${field}`} className="text-sm font-medium"><L10nValue>{label}</L10nValue></label>
          <textarea
            id={`clinical-${field}`}
            value={value[field] ?? ""}
            maxLength={4000}
            rows={3}
            onChange={(event) => onChange(field, event.target.value)}
            className={`${inputClass(false)} resize-y`}
          />
          <p className="text-xs text-muted-foreground"><L10n>{"Tối đa 4.000 ký tự. Để trống và lưu sẽ xóa mục này."}</L10n></p>
        </div>
      ))}</L10nValue>
    </fieldset>
  );
}

export function ClinicalContentSummary({
  sections,
}: {
  sections: Partial<ClinicalContentDTO>;
}) {
  const populated = clinicalContentSections.filter(([field]) => Boolean(sections[field]?.trim()));
  if (populated.length === 0) return null;

  return (
    <section aria-labelledby="structured-clinical-content-heading" className="mt-5 border-t border-border pt-4">
      <h3 id="structured-clinical-content-heading" className="text-sm font-semibold"><L10n>{"Nội dung khám có cấu trúc"}</L10n></h3>
      <dl className="mt-3 grid gap-4 sm:grid-cols-2">
        <L10nValue>{populated.map(([field, label]) => (
          <div key={field}>
            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10nValue>{label}</L10nValue></dt>
            <dd className="mt-1 whitespace-pre-wrap text-sm leading-6">{sections[field]}</dd>
          </div>
        ))}</L10nValue>
      </dl>
    </section>
  );
}
