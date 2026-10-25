"use client";

import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReportDepartmentName } from "./ReportDepartmentName";

import { useCallback, useId, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import { reportApi } from "../api";
import { useReportRequest } from "../useReportRequest";
import { localReportDate, validateDailyReportQuery, type DailyReportQuery } from "../validation";
import { ReportRequestFeedback } from "./ReportRequestFeedback";
import { ReportSessionBoundary } from "./ReportSessionBoundary";

interface DailyReportViewProps {
  initialQuery?: DailyReportQuery;
  autoLoad?: boolean;
  onQuerySubmit?: (query: DailyReportQuery) => boolean;
}

export function DailyReportView({ initialQuery, autoLoad = false, onQuerySubmit }: DailyReportViewProps) {
  return <ReportSessionBoundary><DailyReportContent initialQuery={initialQuery} autoLoad={autoLoad} onQuerySubmit={onQuerySubmit} /></ReportSessionBoundary>;
}

function DailyReportContent({ initialQuery, autoLoad = false, onQuerySubmit }: DailyReportViewProps) {
  const initialDate = initialQuery?.date ?? "";
  const initialDepartment = initialQuery?.departmentId;
  const [date, setDate] = useState(initialDate);
  const [departmentId, setDepartmentId] = useState(initialDepartment ?? "");
  const [scopeReset, setScopeReset] = useState(0);
  const [validationError, setValidationError] = useState<string | null>(null);
  const errorId = useId();
  const form = useRef<HTMLFormElement>(null);
  const initialRequest = useCallback(() => reportApi.daily(initialDate, initialDepartment), [initialDate, initialDepartment]);
  const { state, load, retry, clear } = useReportRequest(initialRequest, autoLoad && validateDailyReportQuery({ date: initialDate, departmentId: initialDepartment }) === null);
  const loading = state.status === "loading";

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = { date: date.trim(), departmentId: departmentId.trim() || undefined };
    const error = validateDailyReportQuery(query);
    setValidationError(error);
    if (error) {
      clear();
      form.current?.querySelector<HTMLInputElement>(validateDailyReportQuery({ date: query.date }) ? "#report-date" : "#report-department-id")?.focus();
      return;
    }
    if (onQuerySubmit?.(query)) return;
    load(() => reportApi.daily(query.date, query.departmentId));
  }

  return (
    <section className="mt-6" aria-label="Báo cáo ngày" aria-busy={loading}>
      <div className="mb-3 flex flex-wrap gap-2" aria-label="Chọn nhanh ngày báo cáo">
        {[0, 1].map(offset => <Button type="button" key={offset} onClick={() => {
          const day = new Date(); day.setDate(day.getDate() - offset); setDate(localReportDate(day)); setValidationError(null);
        }}>{offset === 0 ? "Hôm nay" : "Hôm qua"}</Button>)}
      </div>
      <form ref={form} onSubmit={submit} noValidate className="grid items-end gap-4 rounded-xl border border-border bg-surface p-4 sm:p-5 md:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_auto]">
        <Field label="Ngày báo cáo" htmlFor="report-date">
          <input id="report-date" type="date" value={date} required onChange={(event) => { setDate(event.target.value); setValidationError(null); }} aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
        </Field>
        <Field label="Khoa (tùy chọn)" htmlFor="report-department-id">
          <ReferenceInput key={scopeReset} kind="department" id="report-department-id" value={departmentId} onValueChange={(id) => { setDepartmentId(id); setValidationError(null); }} placeholder="Tìm khoa theo tên" aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
          {departmentId.trim() && <ReportDepartmentName id={departmentId.trim()} showId={false} />}
        </Field>
        <Button type="submit" variant="primary" loading={loading}>Xem báo cáo</Button>
        {validationError && <p id={errorId} role="alert" className="text-sm text-danger md:col-span-3">{validationError}</p>}
      </form>
      {state.status === "success" && (date !== state.data.reportDate || (departmentId.trim() || null) !== (state.data.departmentId || null)) &&
        <p role="status" className="mt-4 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">
          Bạn đã đổi bộ lọc. Bấm xem báo cáo để cập nhật; kết quả bên dưới vẫn thuộc kỳ và khoa đã tải trước đó.
        </p>}
      <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
        <span>Để trống khoa để xem toàn bệnh viện.</span>
        <Button type="button" onClick={() => { setDepartmentId(""); setScopeReset(v => v + 1); setValidationError(null); }}>Bỏ chọn khoa</Button>
      </div>
      <ReportRequestFeedback state={state} idle="Chọn ngày để xem báo cáo hàng ngày." loading="Đang tải báo cáo…" onRetry={retry} />
      {state.status === "success" && (
        <section className="mt-6 rounded-xl border border-border bg-surface p-4 sm:p-5">
          <div className="flex flex-col gap-3 border-b border-border pb-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0">
              <h2 className="text-lg font-semibold">Báo cáo ngày {formatLocalDate(state.data.reportDate)}</h2>
              <p className="mt-1 break-all text-sm text-muted-foreground">Phạm vi: <ReportDepartmentName id={state.data.departmentId} /></p>
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
