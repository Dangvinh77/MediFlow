"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { OutpatientAvailablePeriods } from "./OutpatientAvailablePeriods";

import { ReferenceInput } from "@/components/references/ReferenceInput";
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
import { formatLocalDate } from "@/lib/format";
import { localReportDate } from "../validation";

interface Props {
  initialQuery: OutpatientReportQuery;
  autoLoad: boolean;
  /** No explicit URL filters: open on the newest accepted period instead of an empty "today". */
  discoverLatest?: boolean;
  onQuerySubmit: (query: OutpatientReportQuery) => boolean;
}

export function OutpatientReportView({ initialQuery, autoLoad, discoverLatest = false, onQuerySubmit }: Props) {
  return <ReportSessionBoundary><OutpatientReportContent initialQuery={initialQuery} autoLoad={autoLoad} discoverLatest={discoverLatest} onQuerySubmit={onQuerySubmit} /></ReportSessionBoundary>;
}

function OutpatientReportContent({ initialQuery, autoLoad, discoverLatest = false, onQuerySubmit }: Props) {
  const { localize } = useLocale();
  const { mode, fromDate: initialFrom, toDate: initialTo, year: initialYear, departmentId: initialDepartment } = initialQuery;
  const [fromDate, setFromDate] = useState(initialFrom);
  const [toDate, setToDate] = useState(initialTo);
  const [year, setYear] = useState(Number.isFinite(initialYear) ? String(initialYear) : "");
  const [departmentId, setDepartmentId] = useState(initialDepartment ?? "");
  const [scopeReset, setScopeReset] = useState(0);
  const [submittedQuery, setSubmittedQuery] = useState(initialQuery);
  const [errors, setErrors] = useState<OutpatientQueryErrors>(() => validateOutpatientQuery(initialQuery));
  const form = useRef<HTMLFormElement>(null);
  const id = useId();
  const initialRequest = useCallback(() => mode === "daily"
    ? reportApi.outpatientDaily(initialFrom, initialTo, initialDepartment)
    : reportApi.outpatientMonthly(initialYear, initialDepartment), [mode, initialFrom, initialTo, initialYear, initialDepartment]);
  const { state, load, retry, clear } = useReportRequest(initialRequest, autoLoad && Object.keys(validateOutpatientQuery(initialQuery)).length === 0);
  const [autoDiscover, setAutoDiscover] = useState(discoverLatest);


  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = { mode, fromDate, toDate, year: Number(year), departmentId: departmentId.trim() || undefined };
    const nextErrors = validateOutpatientQuery(query);
    setErrors(nextErrors);
    const first = Object.keys(nextErrors)[0];
    if (first) { clear(); form.current?.querySelector<HTMLInputElement>(`[name="${first}"]`)?.focus(); return; }
    if (onQuerySubmit(query)) return;
    setSubmittedQuery(query);
    load(() => mode === "daily" ? reportApi.outpatientDaily(query.fromDate, query.toDate, query.departmentId) : reportApi.outpatientMonthly(query.year, query.departmentId));
  }

  function inputProps(field: keyof OutpatientQueryErrors) {
    return { id: `${id}-${field}`, name: field, "aria-invalid": !!errors[field], "aria-describedby": errors[field] ? `${id}-${field}-error` : undefined, className: controlClassName };
  }

  return (
    <section className="mt-6 min-w-0" aria-label={localize(mode === "daily" ? "Báo cáo ngoại trú theo ngày" : "Báo cáo ngoại trú theo tháng")} aria-busy={state.status === "loading"}>
      <L10nValue>{mode === "daily" && <div className="mb-3 flex flex-wrap gap-2" aria-label={localize("Chọn nhanh kỳ báo cáo")}>
        <L10nValue>{["Hôm nay", "7 ngày gần nhất", "Tháng này"].map((label, index) => <Button type="button" key={label} onClick={() => {
          const end = new Date(); const start = new Date(end);
          if (index === 1) start.setDate(start.getDate() - 6);
          if (index === 2) start.setDate(1);
          setFromDate(localReportDate(start)); setToDate(localReportDate(end)); setErrors({});
        }}><L10nValue>{label}</L10nValue></Button>)}</L10nValue>
      </div>}</L10nValue>
      <form ref={form} onSubmit={submit} noValidate className="grid items-start gap-4 rounded-xl border border-border bg-surface p-4 sm:grid-cols-2 sm:p-5 xl:grid-cols-4">
        <L10nValue>{mode === "daily" ? <>
          <Field label={localize("Từ ngày")} htmlFor={`${id}-fromDate`} error={errors.fromDate}><input {...inputProps("fromDate")} type="date" min="2000-01-01" max="2100-12-31" value={fromDate} onChange={(event) => { setFromDate(event.target.value); setErrors({}); }} /></Field>
          <Field label={localize("Đến ngày")} htmlFor={`${id}-toDate`} error={errors.toDate}><input {...inputProps("toDate")} type="date" min="2000-01-01" max="2100-12-31" value={toDate} onChange={(event) => { setToDate(event.target.value); setErrors({}); }} /></Field>
        </> : <Field label={localize("Năm báo cáo")} htmlFor={`${id}-year`} error={errors.year}><input {...inputProps("year")} type="number" min={2000} max={2100} step={1} value={year} onChange={(event) => { setYear(event.target.value); setErrors({}); }} /></Field>}</L10nValue>
        <Field label={localize("Khoa (tùy chọn)")} htmlFor={`${id}-departmentId`} error={errors.departmentId}><ReferenceInput key={scopeReset} kind="department" {...inputProps("departmentId")} value={departmentId} onValueChange={(id) => { setDepartmentId(id); setErrors({}); }} placeholder={localize("Tìm khoa theo tên")} /><L10nValue>{departmentId.trim() && <ReportDepartmentName id={departmentId.trim()} showId={false} />}</L10nValue></Field>
        <Button type="submit" variant="primary" loading={state.status === "loading"} className="sm:mt-[1.625rem]"><L10n>{"Xem báo cáo"}</L10n></Button>
      </form>
      <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
        <span><L10n>{"Để trống khoa để xem toàn bệnh viện."}</L10n></span>
        <Button type="button" onClick={() => { setDepartmentId(""); setScopeReset(value => value + 1); setErrors({}); }}><L10n>{"Bỏ chọn khoa"}</L10n></Button>
      </div>
      <OutpatientAvailablePeriods key={`${mode}:${departmentId}`} mode={mode} departmentId={departmentId.trim() || undefined}
        autoSelectLatest={autoDiscover} onSelect={(period, automatic) => {
        clear(); setFromDate(period.fromDate); setToDate(period.toDate); setYear(period.fromDate.slice(0,4));
        setDepartmentId(period.departmentId ?? ""); setScopeReset(value => value+1); setErrors({});
        setAutoDiscover(false);
        const query = { mode, fromDate: period.fromDate, toDate: period.toDate, year: Number(period.fromDate.slice(0, 4)),
          departmentId: period.departmentId ?? undefined };
          if (!automatic && onQuerySubmit(query)) return;
        setSubmittedQuery(query);
        load(() => mode === "daily" ? reportApi.outpatientDaily(query.fromDate, query.toDate, query.departmentId) : reportApi.outpatientMonthly(query.year, query.departmentId));
      }} />
      <L10nValue>{state.status === "success" && ((departmentId.trim() || undefined) !== submittedQuery.departmentId
        || (mode === "daily" ? fromDate !== submittedQuery.fromDate || toDate !== submittedQuery.toDate : Number(year) !== submittedQuery.year)) &&
        <p role="status" className="mt-4 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm"><L10n>{"Bạn đã đổi bộ lọc. Bấm xem báo cáo để cập nhật; kết quả bên dưới vẫn thuộc kỳ và khoa đã tải trước đó."}</L10n></p>}</L10nValue>
      <ReportRequestFeedback submittedScope={<><L10nValue>{submittedQuery.mode === "daily" ? `Từ ${formatLocalDate(submittedQuery.fromDate)} đến ${formatLocalDate(submittedQuery.toDate)}` : `Năm ${submittedQuery.year}`}</L10nValue> · <ReportDepartmentName id={submittedQuery.departmentId} showId /></>} state={state} idle="Chọn phạm vi để xem báo cáo ngoại trú đã đối soát." loading="Đang tải báo cáo ngoại trú…" onRetry={retry} />
      <L10nValue>{state.status === "success" && <OutpatientReportResult report={state.data} />}</L10nValue>
    </section>
  );
}
