import type {
  DiagnosisFormValues,
  MedicalRecordFieldErrors,
  MedicalRecordFormField,
} from "../form";

interface DiagnosisFormFieldsProps {
  values: DiagnosisFormValues;
  errors: MedicalRecordFieldErrors;
  disabled?: boolean;
  onChange: (field: MedicalRecordFormField, value: string) => void;
}

export function DiagnosisFormFields({
  values,
  errors,
  disabled = false,
  onChange,
}: DiagnosisFormFieldsProps) {
  return (
    <div className="grid gap-5 md:grid-cols-2">
      <FormField label="Tên chẩn đoán" name="diagnosisName" error={errors.diagnosisName} required>
        <input
          id="diagnosisName"
          value={values.diagnosisName}
          disabled={disabled}
          maxLength={255}
          aria-invalid={errors.diagnosisName ? true : undefined}
          aria-describedby={errors.diagnosisName ? "diagnosisName-error" : undefined}
          onChange={(event) => onChange("diagnosisName", event.target.value)}
          className={inputClass(Boolean(errors.diagnosisName))}
        />
      </FormField>
      <FormField label="Mã ICD" name="icdCode" error={errors.icdCode} hint="Ví dụ: J00 hoặc E11.9">
        <input
          id="icdCode"
          value={values.icdCode}
          disabled={disabled}
          maxLength={6}
          autoCapitalize="characters"
          aria-invalid={errors.icdCode ? true : undefined}
          aria-describedby={errors.icdCode ? "icdCode-error" : undefined}
          onChange={(event) => onChange("icdCode", event.target.value.toUpperCase())}
          className={inputClass(Boolean(errors.icdCode))}
        />
      </FormField>
      <FormField label="Mô tả chẩn đoán" name="description" error={errors.description} hint={`${values.description.length}/2000 ký tự`} wide>
        <textarea
          id="description"
          value={values.description}
          disabled={disabled}
          maxLength={2000}
          rows={3}
          aria-invalid={errors.description ? true : undefined}
          aria-describedby={errors.description ? "description-error" : undefined}
          onChange={(event) => onChange("description", event.target.value)}
          className={`${inputClass(Boolean(errors.description))} resize-y`}
        />
      </FormField>
    </div>
  );
}

export function inputClass(invalid: boolean): string {
  return [
    "min-h-11 w-full rounded-lg border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60",
    invalid ? "border-danger" : "border-border",
  ].join(" ");
}

function FormField({
  label,
  name,
  error,
  hint,
  required = false,
  wide = false,
  children,
}: {
  label: string;
  name: MedicalRecordFormField;
  error?: string;
  hint?: string;
  required?: boolean;
  wide?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className={`flex flex-col gap-1${wide ? " md:col-span-2" : ""}`}>
      <label htmlFor={name} className="text-sm font-medium">
        {label}{required ? <span aria-hidden="true"> *</span> : null}
      </label>
      {children}
      {error ? <p id={`${name}-error`} className="text-xs text-danger">{error}</p> : null}
      {!error && hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}
