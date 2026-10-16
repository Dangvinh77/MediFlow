"use client";

import { useCallback, useId, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import { reportApi } from "../api";
import { useReportRequest } from "../useReportRequest";
import { validateDailyReportQuery, type DailyReportQuery } from "../validation";
import { ReportRequestFeedback } from "./ReportRequestFeedback";

interface DailyReportViewProps {
  initialQuery?: DailyReportQuery;
  autoLoad?: boolean;
  onQuerySubmit?: (query: DailyReportQuery) => boolean;
}

export function DailyReportView({ initialQuery, autoLoad = false, onQuerySubmit }: DailyReportViewProps) {
  const initialDate = initialQuery?.date ?? "";
  const initialDepartment = initialQuery?.departmentId;
  const [date, setDate] = useState(initialDate);
  const [departmentId, setDepartmentId] = useState(initialDepartment ?? "");
  const [validationError, setValidationError] = useState<string | null>(null);
  const errorId = useId();
  const initialRequest = useCallback(() => reportApi.daily(initialDate, initialDepartment), [initialDate, initialDepartment]);
  const { state, load, retry } = useReportRequest(initialRequest, autoLoad && validateDailyReportQuery({ date: initialDate, departmentId: initialDepartment }) === null);
  const loading = state.status === "loading";

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = { date: date.trim(), departmentId: departmentId.trim() || undefined };
    const error = validateDailyReportQuery(query);
    setValidationError(error);
    if (error || onQuerySubmit?.(query)) return;
    load(() => reportApi.daily(query.date, query.departmentId));
  }

  return (
    <section className="mt-6" aria-label="Báo cáo ngày" aria-busy={loading}>
      <form onSubmit={submit} noValidate className="grid gap-4 rounded-xl border border-border bg-surface p-4 sm:p-5 md:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_auto] md:items-end">
        <Field label="Ngày báo cáo" htmlFor="report-date">
          <input id="report-date" type="date" value={date} required onChange={(event) => { setDate(event.target.value); setValidationError(null); }} aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
        </Field>
        <Field label="Mã khoa (tùy chọn)" htmlFor="report-department-id">
          <input id="report-department-id" value={departmentId} onChange={(event) => { setDepartmentId(event.target.value); setValidationError(null); }} placeholder="Nhập UUID khoa" aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
        </Field>
        <Button type="submit" variant="primary" loading={loading}>Xem báo cáo</Button>
        {validationError && <p id={errorId} role="alert" className="text-sm text-danger md:col-span-3">{validationError}</p>}
      </form>
      <ReportRequestFeedback state={state} idle="Chọn ngày để xem báo cáo hàng ngày." loading="Đang tải báo cáo…" onRetry={retry} />
      {state.status === "success" && (
        <section className="mt-6 rounded-xl border border-border bg-surface p-4 sm:p-5">
          <div className="flex flex-col gap-3 border-b border-border pb-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0">
              <h2 className="text-lg font-semibold">Báo cáo ngày {formatLocalDate(state.data.reportDate)}</h2>
              <p className="mt-1 break-all text-sm text-muted-foreground">Phạm vi: {state.data.departmentId ?? "Toàn bệnh viện"}</p>
            </div>
            <StatusBadge tone="success">Đã tải</StatusBadge>
          </div>
          <dl className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {[
              ["Lượt khám", state.data.visitCount.toLocaleString("vi-VN")],
              ["Xét nghiệm", state.data.labCount.toLocaleString("vi-VN")],
              ["Đơn thuốc", state.data.prescriptionCount.toLocaleString("vi-VN")],
              ["Doanh thu", formatDecimal(state.data.revenue)],
            ].map(([label, value]) => (
              <div key={label} className="min-w-0 rounded-lg border border-border bg-surface-muted p-4">
                <dt className="text-sm text-muted-foreground">{label}</dt>
                <dd className="mt-1 break-words text-2xl font-semibold tabular-nums">{value}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}
    </section>
  );
}
