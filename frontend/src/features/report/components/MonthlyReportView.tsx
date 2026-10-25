"use client";

import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReportDepartmentName } from "./ReportDepartmentName";

import { useCallback, useId, useRef, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { formatDecimal } from "@/lib/format";
import { reportApi } from "../api";
import { useReportRequest } from "../useReportRequest";
import { validateMonthlyReportQuery, type MonthlyReportQuery } from "../validation";
import { DailyDetailsTable } from "./DailyDetailsTable";
import { MonthlyRevenueChart } from "./MonthlyRevenueChart";
import { ReportRequestFeedback } from "./ReportRequestFeedback";
import { ReportSessionBoundary } from "./ReportSessionBoundary";

interface MonthlyReportViewProps {
  initialQuery?: MonthlyReportQuery;
  autoLoad?: boolean;
  /** True when URL navigation will mount the submitted query; false reloads the current one. */
  onQuerySubmit?: (query: MonthlyReportQuery) => boolean;
}

export function MonthlyReportView({ initialQuery, autoLoad = false, onQuerySubmit }: MonthlyReportViewProps) {
  return <ReportSessionBoundary><MonthlyReportContent initialQuery={initialQuery} autoLoad={autoLoad} onQuerySubmit={onQuerySubmit} /></ReportSessionBoundary>;
}

function MonthlyReportContent({ initialQuery, autoLoad = false, onQuerySubmit }: MonthlyReportViewProps) {
  const [defaults] = useState<MonthlyReportQuery>(() => ({ month: new Date().getMonth() + 1, year: new Date().getFullYear() }));
  const initial = initialQuery ?? defaults;
  const initialDepartment = initial.departmentId;
  const [month, setMonth] = useState(Number.isFinite(initial.month) ? String(initial.month) : "");
  const [year, setYear] = useState(Number.isFinite(initial.year) ? String(initial.year) : "");
  const [departmentId, setDepartmentId] = useState(initialDepartment ?? "");
  const [scopeReset, setScopeReset] = useState(0);
  const [validationError, setValidationError] = useState<string | null>(null);
  const errorId = useId();
  const form = useRef<HTMLFormElement>(null);
  const initialRequest = useCallback(() => reportApi.monthly(initial.month, initial.year, initialDepartment), [initial.month, initial.year, initialDepartment]);
  const { state, load, retry, clear } = useReportRequest(initialRequest, autoLoad && validateMonthlyReportQuery(initial) === null);
  const loading = state.status === "loading";

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = { month: Number(month), year: Number(year), departmentId: departmentId.trim() || undefined };
    const error = validateMonthlyReportQuery(query);
    setValidationError(error);
    if (error) {
      clear();
      const selector = validateMonthlyReportQuery({ month: query.month, year: 2026 }) ? "#report-month"
        : validateMonthlyReportQuery({ month: 1, year: query.year }) ? "#report-year" : "#monthly-department";
      form.current?.querySelector<HTMLInputElement>(selector)?.focus();
      return;
    }
    if (onQuerySubmit?.(query)) return;
    load(() => reportApi.monthly(query.month, query.year, query.departmentId));
  }

  return (
    <section className="mt-6" aria-label="Báo cáo tháng" aria-busy={loading}>
      <div className="mb-3 flex flex-wrap gap-2" aria-label="Chọn nhanh tháng báo cáo">
        {[0, 1].map(offset => <Button type="button" key={offset} onClick={() => {
          const day = new Date(); day.setDate(1); day.setMonth(day.getMonth() - offset);
          setMonth(String(day.getMonth() + 1)); setYear(String(day.getFullYear())); setValidationError(null);
        }}>{offset === 0 ? "Tháng này" : "Tháng trước"}</Button>)}
      </div>
      <form ref={form} onSubmit={submit} noValidate className="grid items-end gap-4 rounded-xl border border-border bg-surface p-4 sm:grid-cols-2 sm:p-5 xl:grid-cols-[minmax(0,0.7fr)_minmax(0,0.8fr)_minmax(0,1.5fr)_auto]">
        <Field label="Tháng" htmlFor="report-month">
          <select id="report-month" value={month} onChange={(event) => { setMonth(event.target.value); setValidationError(null); }} aria-describedby={validationError ? errorId : undefined} className={controlClassName}>
            {!month && <option value="">Chọn tháng</option>}
            {Array.from({ length: 12 }, (_, i) => <option key={i + 1} value={String(i + 1)}>Tháng {i + 1}</option>)}
          </select>
        </Field>
        <Field label="Năm" htmlFor="report-year">
          <input id="report-year" type="number" min={2000} max={2100} step={1} value={year} onChange={(event) => { setYear(event.target.value); setValidationError(null); }} aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
        </Field>
        <Field label="Khoa (tùy chọn)" htmlFor="monthly-department">
          <ReferenceInput key={scopeReset} kind="department" id="monthly-department" value={departmentId} onValueChange={(id) => { setDepartmentId(id); setValidationError(null); }} placeholder="Tìm khoa theo tên" aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
          {departmentId.trim() && <ReportDepartmentName id={departmentId.trim()} showId={false} />}
        </Field>
        <Button type="submit" variant="primary" loading={loading}>Xem báo cáo</Button>
        {validationError && <p id={errorId} role="alert" className="text-sm text-danger sm:col-span-2 xl:col-span-4">{validationError}</p>}
      </form>
      {state.status === "success" && (Number(month) !== state.data.month || Number(year) !== state.data.year || (departmentId.trim() || null) !== (state.data.departmentId || null)) &&
        <p role="status" className="mt-4 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">
          Bạn đã đổi bộ lọc. Bấm xem báo cáo để cập nhật; kết quả bên dưới vẫn thuộc kỳ và khoa đã tải trước đó.
        </p>}
      <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
        <span>Để trống khoa để xem toàn bệnh viện.</span>
        <Button type="button" onClick={() => { setDepartmentId(""); setScopeReset(v => v + 1); setValidationError(null); }}>Bỏ chọn khoa</Button>
      </div>
      <ReportRequestFeedback state={state} idle="Chọn tháng, năm và khoa để xem báo cáo." loading="Đang tải báo cáo tháng…" onRetry={retry} />
      {state.status === "success" && (
        <div className="mt-6">
          <div className="grid gap-5 xl:grid-cols-[minmax(0,2.2fr)_minmax(220px,1fr)]">
            <section className="min-w-0 rounded-xl border border-border bg-surface p-4 sm:p-5">
              <div className="mb-4 flex flex-wrap items-center gap-3">
                <h2 className="text-base font-semibold">Doanh thu theo ngày</h2>
                <span className="rounded-full bg-surface-muted px-2.5 py-1 text-xs text-muted-foreground">{state.data.month}/{state.data.year}</span>
              </div>
              {state.data.dailyDetails.length ? <MonthlyRevenueChart days={state.data.dailyDetails} /> : <AsyncState kind="empty" message="Báo cáo không có chi tiết theo ngày." />}
            </section>
            <section className="min-w-0 rounded-xl border border-border bg-surface p-4 sm:p-5">
              <h2 className="text-base font-semibold">Tháng {state.data.month}/{state.data.year}</h2>
              <p className="mt-1 break-all text-xs text-muted-foreground">Phạm vi: <ReportDepartmentName id={state.data.departmentId} /></p>
              <dl className="mt-4 divide-y divide-border">
                <div className="py-4"><dt className="text-sm text-muted-foreground">Tổng doanh thu</dt><dd className="mt-2 break-words text-3xl font-semibold tracking-tight tabular-nums">{formatDecimal(state.data.totalRevenue)}</dd></div>
                <div className="py-4"><dt className="text-sm text-muted-foreground">Số hóa đơn</dt><dd className="mt-2 text-3xl font-semibold tabular-nums">{state.data.invoiceCount.toLocaleString("vi-VN")}</dd></div>
              </dl>
              <p className="mt-2 rounded-lg bg-surface-muted p-3 text-xs text-muted-foreground">Nguồn số liệu: Report</p>
            </section>
          </div>
          <DailyDetailsTable days={state.data.dailyDetails} />
        </div>
      )}
    </section>
  );
}
