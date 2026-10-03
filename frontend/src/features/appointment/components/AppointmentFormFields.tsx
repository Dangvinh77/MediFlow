import type {
  AppointmentFieldErrors,
  AppointmentFormField,
  AppointmentFormValues,
} from "../form";

interface AppointmentFormFieldsProps {
  values: AppointmentFormValues;
  errors: AppointmentFieldErrors;
  includeReferences: boolean;
  disabled?: boolean;
  onChange: (field: AppointmentFormField, value: string) => void;
}

export function AppointmentFormFields({
  values,
  errors,
  includeReferences,
  disabled = false,
  onChange,
}: AppointmentFormFieldsProps) {
  const accessibility = (field: AppointmentFormField) => ({
    "aria-invalid": errors[field] ? true : undefined,
    "aria-describedby": errors[field] ? `${field}-error` : undefined,
  });

  return (
    <div className="grid gap-5 md:grid-cols-2">
      {includeReferences ? (
        <>
          <FormField label="Mã bệnh nhân" name="patientId" error={errors.patientId} required hint="Nhập UUID từ hồ sơ bệnh nhân.">
            <input
              {...accessibility("patientId")}
              id="patientId"
              value={values.patientId}
              disabled={disabled}
              autoComplete="off"
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              onChange={(event) => onChange("patientId", event.target.value)}
              className={inputClass(Boolean(errors.patientId))}
            />
          </FormField>
          <FormField label="Mã bác sĩ" name="doctorId" error={errors.doctorId} required hint="Nhập UUID nhân sự bác sĩ.">
            <input
              {...accessibility("doctorId")}
              id="doctorId"
              value={values.doctorId}
              disabled={disabled}
              autoComplete="off"
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              onChange={(event) => onChange("doctorId", event.target.value)}
              className={inputClass(Boolean(errors.doctorId))}
            />
          </FormField>
          <FormField label="Mã khoa" name="departmentId" error={errors.departmentId} required hint="Nhập UUID khoa tiếp nhận.">
            <input
              {...accessibility("departmentId")}
              id="departmentId"
              value={values.departmentId}
              disabled={disabled}
              autoComplete="off"
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              onChange={(event) => onChange("departmentId", event.target.value)}
              className={inputClass(Boolean(errors.departmentId))}
            />
          </FormField>
        </>
      ) : null}

      <FormField label="Ngày hẹn" name="appointmentDate" error={errors.appointmentDate} required>
        <input
          {...accessibility("appointmentDate")}
          id="appointmentDate"
          type="date"
          value={values.appointmentDate}
          disabled={disabled}
          onChange={(event) => onChange("appointmentDate", event.target.value)}
          className={inputClass(Boolean(errors.appointmentDate))}
        />
      </FormField>
      <FormField label="Giờ hẹn" name="appointmentTime" error={errors.appointmentTime} required hint="Backend xác nhận khung giờ và xung đột lịch.">
        <input
          {...accessibility("appointmentTime")}
          id="appointmentTime"
          type="time"
          value={values.appointmentTime}
          disabled={disabled}
          onChange={(event) => onChange("appointmentTime", event.target.value)}
          className={inputClass(Boolean(errors.appointmentTime))}
        />
      </FormField>
      <FormField label="Lý do khám" name="reason" error={errors.reason} hint={`${values.reason.length}/1000 ký tự`}>
        <textarea
          {...accessibility("reason")}
          id="reason"
          value={values.reason}
          disabled={disabled}
          maxLength={1000}
          rows={4}
          onChange={(event) => onChange("reason", event.target.value)}
          className={`${inputClass(Boolean(errors.reason))} resize-y md:col-span-2`}
        />
      </FormField>
    </div>
  );
}

function inputClass(invalid: boolean): string {
  return [
    "min-h-11 w-full rounded-lg border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60",
    invalid ? "border-danger" : "border-border",
  ].join(" ");
}

function FormField({
  label,
  name,
  error,
  required = false,
  hint,
  children,
}: {
  label: string;
  name: AppointmentFormField;
  error?: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={name === "reason" ? "flex flex-col gap-1 md:col-span-2" : "flex flex-col gap-1"}>
      <label htmlFor={name} className="text-sm font-medium">
        {label}{required ? <span aria-hidden="true"> *</span> : null}
      </label>
      {children}
      {error ? (
        <p id={`${name}-error`} className="text-xs text-danger">{error}</p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}
