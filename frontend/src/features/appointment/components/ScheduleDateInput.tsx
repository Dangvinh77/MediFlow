"use client";

import { useId, type InputHTMLAttributes } from "react";
import { formatLocalDate } from "@/lib/format";

/** Keep the native calendar and ISO value; make its selected day unambiguous in Vietnamese. */
export function ScheduleDateInput({label, id, value, "aria-describedby": describedBy, "aria-invalid": invalid, ...props}: Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {label: string}) {
  const generatedId=useId();
  const inputId=id ?? generatedId;
  const hintId=`${inputId}-selected-date`;
  return <div className="space-y-1 text-sm">
    <label htmlFor={inputId} className="block">{label}</label>
    <input {...props} id={inputId} type="date" value={value} aria-invalid={invalid} aria-describedby={[describedBy,invalid === true || invalid === "true" ? undefined : hintId].filter(Boolean).join(" ") || undefined} />
    <p id={hintId} className="text-xs text-muted-foreground">
      {typeof value === "string" && value ? `Đã chọn: ${formatLocalDate(value)} (ngày/tháng/năm)` : "Chưa chọn ngày"}
      <span className="sr-only">. Định dạng trong ô lịch theo thiết lập trình duyệt.</span>
    </p>
  </div>;
}
