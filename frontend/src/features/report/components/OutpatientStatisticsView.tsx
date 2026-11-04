"use client";

import { useCallback, useId, useState, type FormEvent } from "react";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { getRole } from "@/lib/auth";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import { reportApi } from "../api";
import { validateOutpatientQuery } from "../outpatientQuery";
import { useReportRequest } from "../useReportRequest";
import { localReportDate } from "../validation";
import { DailyRevenueChart, DailyVisitsChart, DepartmentCharts, revenueSeries } from "./OutpatientStatisticsCharts";
import { ReportDepartmentName } from "./ReportDepartmentName";
import { ReportRequestFeedback } from "./ReportRequestFeedback";
import { ReportSessionBoundary } from "./ReportSessionBoundary";

function monthStart(now: Date) {
  return localReportDate(new Date(now.getFullYear(), now.getMonth(), 1));
}

/** Provisional operations: loads the current month on open, unlike the reconciled report below it. */
export function OutpatientStatisticsView() {
  return <ReportSessionBoundary><StatisticsContent /></ReportSessionBoundary>;
}

function StatisticsContent() {
  const id = useId();
  const [now] = useState(() => new Date());
  const [fromDate, setFromDate] = useState(() => monthStart(now));
  const [toDate, setToDate] = useState(() => localReportDate(now));
  const [departmentId, setDepartmentId] = useState("");
  const [error, setError] = useState<string>();
  // MANAGER reads are department-scoped by the backend; wait for an explicit department.
  const needsDepartment = getRole() === "MANAGER";
  const initial = useCallback(() => reportApi.outpatientStatistics(monthStart(now), localReportDate(now)), [now]);
  const { state, load, retry } = useReportRequest(initial, !needsDepartment);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const department = departmentId.trim() || undefined;
    const errors = validateOutpatientQuery({ mode: "daily", fromDate, toDate, year: Number(fromDate.slice(0, 4)), departmentId: department });
    const message = errors.fromDate ?? errors.toDate ?? errors.departmentId;
    setError(message);
    if (message || (needsDepartment && !department)) return;
    load(() => reportApi.outpatientStatistics(fromDate, toDate, department));
  }

  const report = state.status === "success" ? state.data : undefined;
  return <section aria-label="Thống kê vận hành ngoại trú" className="mt-6 min-w-0 space-y-4">
    <div>
      <h2 className="text-lg font-semibold">Thống kê vận hành</h2>
      <p className="mt-1 text-sm text-muted-foreground">Doanh thu khám bệnh, xét nghiệm, thuốc và số lượt khám theo khoa, cập nhật theo nghiệp vụ đã ghi nhận.</p>
    </div>
    <form onSubmit={submit} noValidate className="grid items-end gap-4 rounded-xl border border-border bg-surface p-4 sm:grid-cols-2 xl:grid-cols-4">
      <Field label="Từ ngày" htmlFor={`${id}-from`}>
        <input id={`${id}-from`} type="date" className={controlClassName} value={fromDate} onChange={event => setFromDate(event.target.value)} />
      </Field>
      <Field label="Đến ngày" htmlFor={`${id}-to`}>
        <input id={`${id}-to`} type="date" className={controlClassName} value={toDate} onChange={event => setToDate(event.target.value)} />
      </Field>
      <Field label={needsDepartment ? "Khoa" : "Khoa (tùy chọn)"} htmlFor={`${id}-department`}>
        <ReferenceInput kind="department" id={`${id}-department`} className={controlClassName} value={departmentId}
          onValueChange={setDepartmentId} placeholder="Tìm khoa theo tên" />
      </Field>
      <Button type="submit" variant="primary" loading={state.status === "loading"}>Xem thống kê</Button>
    </form>
    {error && <p role="alert" className="text-sm text-danger">{error}</p>}
    {needsDepartment && state.status === "idle" && <p className="text-sm text-muted-foreground">Chọn khoa được phân quyền rồi bấm xem thống kê.</p>}
    {state.status !== "success" && state.status !== "idle" && <ReportRequestFeedback state={state} idle="" loading="Đang tải thống kê vận hành…" onRetry={retry} />}
    {report && <>
      <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">
        Số liệu tạm tính từ {formatLocalDate(report.fromDate)} đến {formatLocalDate(report.toDate)} · <ReportDepartmentName id={report.departmentId ?? undefined} showId={false} />.
        Chưa đối soát; báo cáo đã đối soát nằm ở phần dưới. Doanh thu là tiền đã thu, chưa trừ hoàn tiền.
        {" "}Chỉ gồm dữ liệu đã đồng bộ về báo cáo, có thể còn thiếu sự kiện. Số 0 nghĩa là chưa ghi nhận dữ liệu, không khẳng định không có hoạt động.
      </p>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6" aria-label="Tổng hợp">
        {([["Lượt khám hoàn tất", report.totals.completedVisits, false],
          ...revenueSeries.filter(item => item.key !== "unclassifiedRevenue" || report.totals.unclassifiedRevenue > 0)
            .map(item => [item.label, report.totals[item.key], true] as const),
          ["Tiền đã hoàn", report.totals.refunds, true]] as const).map(([label, value, money]) =>
          <div key={label} className="rounded-lg border border-border bg-surface p-3">
            <dt className="text-xs text-muted-foreground">{label}</dt>
            <dd className="mt-1 text-lg font-semibold tabular-nums">{formatDecimal(value)}{money ? ` ${report.currency}` : ""}</dd>
          </div>)}
      </dl>
      <div className="grid min-w-0 gap-4 xl:grid-cols-2">
        <DailyRevenueChart report={report} />
        <DailyVisitsChart report={report} />
      </div>
      <DepartmentCharts report={report} />
    </>}
  </section>;
}
