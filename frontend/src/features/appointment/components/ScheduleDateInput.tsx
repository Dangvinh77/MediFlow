"use client";

import { useId, type InputHTMLAttributes } from "react";
import { formatLocalDate } from "@/lib/format";

/** Keep the native calendar and ISO value; the Vietnamese date sits on the label row so controls in a row stay level. */
export function ScheduleDateInput({label, id, value, "aria-describedby": describedBy, "aria-invalid": invalid, ...props}: Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {label: string}) {
  const generatedId=useId();
  const inputId=id ?? generatedId;
  const hintId=`${inputId}-selected-date`;
  return <div className="text-sm">
    <div className="mb-1 flex items-baseline justify-between gap-3">
      <label htmlFor={inputId} className="font-medium">{label}</label>
      <span id={hintId} className="whitespace-nowrap text-xs text-muted-foreground">
        {typeof value === "string" && value ? <><span className="sr-only">Đã chọn: </span>{formatLocalDate(value)}<span className="sr-only"> (ngày/tháng/năm). Định dạng trong ô lịch theo thiết lập trình duyệt.</span></> : "Chưa chọn ngày"}
      </span>
    </div>
    <input {...props} id={inputId} type="date" value={value} aria-invalid={invalid} aria-describedby={[describedBy,invalid === true || invalid === "true" ? undefined : hintId].filter(Boolean).join(" ") || undefined} />
  </div>;
}
