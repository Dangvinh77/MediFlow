"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import { useId, type InputHTMLAttributes } from "react";
import { formatLocalDate } from "@/lib/format";

/** Keep the native calendar and ISO value; the Vietnamese date sits on the label row so controls in a row stay level. */
export function ScheduleDateInput({label, id, value, "aria-describedby": describedBy, "aria-invalid": invalid, ...props}: Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {label: string}) {
  const generatedId=useId();
  const inputId=id ?? generatedId;
  const hintId=`${inputId}-selected-date`;
  return <div className="text-sm">
    <div className="mb-1 flex items-baseline justify-between gap-3">
      <label htmlFor={inputId} className="font-medium"><L10nValue>{label}</L10nValue></label>
      <span id={hintId} className="whitespace-nowrap text-xs text-muted-foreground">
        <L10nValue>{typeof value === "string" && value ? <><span className="sr-only"><L10n>{"Đã chọn: "}</L10n></span><L10nValue>{formatLocalDate(value)}</L10nValue><span className="sr-only"><L10n>{" (ngày/tháng/năm). Định dạng trong ô lịch theo thiết lập trình duyệt."}</L10n></span></> : "Chưa chọn ngày"}</L10nValue>
      </span>
    </div>
    <input {...props} id={inputId} type="date" value={value} aria-invalid={invalid} aria-describedby={[describedBy,invalid === true || invalid === "true" ? undefined : hintId].filter(Boolean).join(" ") || undefined} />
  </div>;
}
