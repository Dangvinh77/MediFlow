"use client";

import { ReportDepartmentName } from "./ReportDepartmentName";

import { useCallback, useId, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { reportApi } from "../api";
import { validateOutpatientQuery, type OutpatientQueryErrors, type OutpatientReportQuery } from "../outpatientQuery";
import { useReportRequest } from "../useReportRequest";
import { OutpatientReportResult } from "./OutpatientReportResult";
import { ReportRequestFeedback } from "./ReportRequestFeedback";
import { ReportSessionBoundary } from "./ReportSessionBoundary";

interface Props {
  initialQuery: OutpatientReportQuery;
  autoLoad: boolean;
  onQuerySubmit: (query: OutpatientReportQuery) => boolean;
}

export function OutpatientReportView({ initialQuery, autoLoad, onQuerySubmit }: Props) {
  return <ReportSessionBoundary><OutpatientReportContent initialQuery={initialQuery} autoLoad={autoLoad} onQuerySubmit={onQuerySubmit} /></ReportSessionBoundary>;
}

function OutpatientReportContent({ initialQuery, autoLoad, onQuerySubmit }: Props) {
  const { mode, fromDate: initialFrom, toDate: initialTo, year: initialYear, departmentId: initialDepartment } = initialQuery;
  const [fromDate, setFromDate] = useState(initialFrom);
  const [toDate, setToDate] = useState(initialTo);
  const [year, setYear] = useState(Number.isFinite(initialYear) ? String(initialYear) : "");
  const [departmentId, setDepartmentId] = useState(initialDepartment ?? "");
  const [errors, setErrors] = useState<OutpatientQueryErrors>(() => validateOutpatientQuery(initialQuery));
  const form = useRef<HTMLFormElement>(null);
  const id = useId();
  const initialRequest = useCallback(() => mode === "daily"
    ? reportApi.outpatientDaily(initialFrom, initialTo, initialDepartment)
    : reportApi.outpatientMonthly(initialYear, initialDepartment), [mode, initialFrom, initialTo, initialYear, initialDepartment]);
  const { state, load, retry, clear } = useReportRequest(initialRequest, autoLoad && Object.keys(validateOutpatientQuery(initialQuery)).length === 0);
  const feedback = state.status === "error" && state.code === "REPORT_NOT_FOUND"
    ? { ...state, message: "Chưa có báo cáo đã đối soát cho phạm vi này. Vui lòng thử lại sau hoặc liên hệ quản trị viên." }
    : state;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = { mode, fromDate, toDate, year: Number(year), departmentId: departmentId.trim() || undefined };
    const nextErrors = validateOutpatientQuery(query);
    setErrors(nextErrors);
    const first = Object.keys(nextErrors)[0];
    if (first) { clear(); form.current?.querySelector<HTMLInputElement>(`[name="${first}"]`)?.focus(); return; }
    if (onQuerySubmit(query)) return;
    load(() => mode === "daily" ? reportApi.outpatientDaily(query.fromDate, query.toDate, query.departmentId) : reportApi.outpatientMonthly(query.year, query.departmentId));
  }

  function inputProps(field: keyof OutpatientQueryErrors) {
    return { id: `${id}-${field}`, name: field, "aria-invalid": !!errors[field], "aria-describedby": errors[field] ? `${id}-${field}-error` : undefined, className: controlClassName };
  }

  return (
    <section className="mt-6 min-w-0" aria-label={mode === "daily" ? "Báo cáo ngoại trú theo ngày" : "Báo cáo ngoại trú theo tháng"} aria-busy={state.status === "loading"}>
      <form ref={form} onSubmit={submit} noValidate className="grid gap-4 rounded-xl border border-border bg-surface p-4 sm:grid-cols-2 sm:p-5 xl:grid-cols-4 xl:items-end">
        {mode === "daily" ? <>
          <Field label="Từ ngày" htmlFor={`${id}-fromDate`} error={errors.fromDate}><input {...inputProps("fromDate")} type="date" min="2000-01-01" max="2100-12-31" value={fromDate} onChange={(event) => { setFromDate(event.target.value); setErrors({}); }} /></Field>
          <Field label="Đến ngày" htmlFor={`${id}-toDate`} error={errors.toDate}><input {...inputProps("toDate")} type="date" min="2000-01-01" max="2100-12-31" value={toDate} onChange={(event) => { setToDate(event.target.value); setErrors({}); }} /></Field>
        </> : <Field label="Năm báo cáo" htmlFor={`${id}-year`} error={errors.year}><input {...inputProps("year")} type="number" min={2000} max={2100} step={1} value={year} onChange={(event) => { setYear(event.target.value); setErrors({}); }} /></Field>}
        <Field label="Mã khoa (tùy chọn)" htmlFor={`${id}-departmentId`} error={errors.departmentId}><input {...inputProps("departmentId")} value={departmentId} onChange={(event) => { setDepartmentId(event.target.value); setErrors({}); }} placeholder="Nhập UUID khoa" />{departmentId.trim() && <ReportDepartmentName id={departmentId.trim()} showId={false} />}</Field>
        <Button type="submit" variant="primary" loading={state.status === "loading"}>Xem báo cáo</Button>
      </form>
      <ReportRequestFeedback state={feedback} idle="Chọn phạm vi để xem báo cáo ngoại trú đã đối soát." loading="Đang tải báo cáo ngoại trú…" onRetry={retry} />
      {state.status === "success" && <OutpatientReportResult report={state.data} />}
    </section>
  );
}
