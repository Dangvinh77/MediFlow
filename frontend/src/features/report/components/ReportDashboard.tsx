"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useId, useState } from "react";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { AsyncState } from "@/components/ui/AsyncState";
import { controlClassName, Field } from "@/components/ui/Field";
import { getRole } from "@/lib/auth";
import { refreshCapabilities, useCapabilities } from "@/lib/capabilities";
import { isUuid } from "@/lib/validation";
import { reportApi } from "../api";
import { commonCutoff, hasActivity, yearRange } from "../dashboardData";
import { useReportRequest } from "../useReportRequest";
import { ReportRequestFeedback } from "./ReportRequestFeedback";
import { ReportSessionBoundary } from "./ReportSessionBoundary";
import { readDashboardQuery, yearOptions, type DashboardTab } from "../dashboardQuery";
import { YearComparison } from "./YearComparison";
import { YearOverview } from "./YearOverview";

const tabs: { key: DashboardTab; label: string }[] = [
  { key: "overview", label: "Tổng quan năm" },
  { key: "compare", label: "So sánh hai năm" },
];

/** Provisional yearly operations. A department head's scope is fixed by their account. */
export function ReportDashboard() {
  return <ReportSessionBoundary><DashboardContent /></ReportSessionBoundary>;
}

function DashboardContent() {
  const { localize } = useLocale();
  const params = useSearchParams();
  const pathname = usePathname();
  const rawQuery = params.toString();
  const id = useId();
  const [now] = useState(() => new Date());
  const currentYear = now.getFullYear();
  const { tab, baseYear, compareYear, departmentId } = readDashboardQuery(new URLSearchParams(rawQuery), currentYear);
  const isDepartmentHead = getRole() === "DOCTOR";
  const capabilities = useCapabilities();
  const assigned = capabilities.data?.managementDepartments ?? [];
  // The shell only loads capabilities for doctors; a manager's fixed department is read here.
  useEffect(() => { if (isDepartmentHead) void refreshCapabilities(); }, [isDepartmentHead]);

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(rawQuery);
    Object.entries(changes).forEach(([name, value]) => { if (value) next.set(name, value); else next.delete(name); });
    window.history.pushState(null, "", `${pathname}?${next}`);
  }

  // The URL can name any department, but a manager only ever reads one of their own.
  const headDepartment = assigned.find(item => item.departmentId === departmentId) ?? assigned[0];
  const scope = isDepartmentHead ? headDepartment?.departmentId : isUuid(departmentId) ? departmentId : undefined;
  const years = yearOptions(currentYear);
  const showResult = !isDepartmentHead || !!scope;

  return <section aria-label={localize("Bảng điều khiển báo cáo")} className="mt-6 min-w-0 space-y-4">
    <nav aria-label={localize("Kiểu xem báo cáo")} className="flex flex-wrap gap-x-6 border-b border-border">
      <L10nValue>{tabs.map(item => <button key={item.key} type="button" onClick={() => update({ tab: item.key })} aria-current={tab === item.key ? "page" : undefined}
        className={`inline-flex min-h-11 items-center border-b-2 px-1 py-3 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${tab === item.key ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}><L10nValue>{item.label}</L10nValue></button>)}</L10nValue>
    </nav>
    <div className="grid items-start gap-4 rounded-xl border border-border bg-surface p-4 sm:grid-cols-2 xl:grid-cols-3">
      <Field label={localize(tab === "compare" ? "Năm xem" : "Năm báo cáo")} htmlFor={`${id}-year`}>
        <select id={`${id}-year`} className={controlClassName} value={baseYear} onChange={event => update({ baseYear: event.target.value })}>
          <L10nValue>{years.map(year => <option key={year} value={year}><L10nValue>{year}</L10nValue></option>)}</L10nValue>
        </select>
      </Field>
      <L10nValue>{tab === "compare" && <Field label={localize("So với năm")} htmlFor={`${id}-compare`}>
        <select id={`${id}-compare`} className={controlClassName} value={compareYear} onChange={event => update({ compareYear: event.target.value })}>
          <L10nValue>{years.filter(year => year !== baseYear).map(year => <option key={year} value={year}><L10nValue>{year}</L10nValue></option>)}</L10nValue>
        </select>
      </Field>}</L10nValue>
      <L10nValue>{isDepartmentHead
        ? assigned.length > 1
          ? <Field label={localize("Khoa phụ trách")} htmlFor={`${id}-department`}>
              <select id={`${id}-department`} className={controlClassName} value={scope} onChange={event => update({ dept: event.target.value })}>
                <L10nValue>{assigned.map(item => <option key={item.departmentId} value={item.departmentId}>{item.departmentName}</option>)}</L10nValue>
              </select>
            </Field>
          : <div className="space-y-1.5">
              <p className="text-sm font-medium text-foreground"><L10n>{"Khoa phụ trách"}</L10n></p>
              <p className="flex min-h-11 items-center gap-2 rounded-lg border border-border bg-surface-muted px-3 py-2 text-sm font-medium">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="shrink-0 text-muted-foreground"><rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>
                <L10nValue>{headDepartment?.departmentName ?? (capabilities.error ? "Chưa xác định được khoa" : "Đang xác định khoa…")}</L10nValue>
              </p>
            </div>
        : <Field label={localize("Khoa (tùy chọn)")} htmlFor={`${id}-department`} hint="Để trống để xem toàn viện.">
            <ReferenceInput kind="department" id={`${id}-department`} className={controlClassName} value={departmentId}
              onValueChange={value => update({ dept: value })} placeholder={localize("Tìm khoa theo tên")} />
          </Field>}</L10nValue>
    </div>
    <L10nValue>{isDepartmentHead && capabilities.error && <p role="alert" className="text-sm text-danger"><L10nValue>{capabilities.error}</L10nValue></p>}</L10nValue>
    <L10nValue>{isDepartmentHead && capabilities.data && !assigned.length && <p role="status" className="rounded-xl border border-border bg-surface p-4 text-sm text-muted-foreground"><L10n>{"Tài khoản chưa được gán khoa phụ trách. Vui lòng liên hệ quản trị viên."}</L10n></p>}</L10nValue>
    <L10nValue>{showResult && <DashboardResult key={`${tab}:${baseYear}:${compareYear}:${scope ?? ""}`} tab={tab} baseYear={baseYear} compareYear={compareYear} departmentId={scope} now={now} />}</L10nValue>
    <p className="text-xs text-muted-foreground"><L10n>{"Số liệu tạm tính từ các nghiệp vụ đã ghi nhận, có thể cập nhật chậm. Doanh thu là tiền đã thu, chưa trừ hoàn tiền."}</L10n></p>
  </section>;
}

function DashboardResult({ tab, baseYear, compareYear, departmentId, now }: { tab: DashboardTab; baseYear: number; compareYear: number; departmentId?: string; now: Date }) {
  const { localize } = useLocale();
  const request = useCallback(async () => {
    const read = (fromDate: string, toDate: string) => reportApi.outpatientStatistics(fromDate, toDate, departmentId);
    const yearly = (year: number) => { const { fromDate, toDate } = yearRange(year, now); return read(fromDate, toDate); };
    if (tab === "overview") return { base: await yearly(baseYear) };
    const [base, compare] = await Promise.all([yearly(baseYear), yearly(compareYear)]);
    const cutoff = commonCutoff(base, compare);
    // Per-department figures only come whole, so the longer year is read again up to the cutoff.
    const cut = (report: typeof base, year: number) => report.toDate.slice(5) === cutoff ? report : read(`${year}-01-01`, `${year}-${cutoff}`);
    const [basePeriod, comparePeriod] = await Promise.all([cut(base, baseYear), cut(compare, compareYear)]);
    return { base, comparison: { compare, basePeriod, comparePeriod } };
  }, [tab, baseYear, compareYear, departmentId, now]);
  const { state, retry } = useReportRequest(request, true);

  if (state.status !== "success") return <ReportRequestFeedback state={state} idle="" loading="Đang tải báo cáo…" onRetry={retry} />;
  const { base, comparison } = state.data;
  if (comparison) {
    if (!hasActivity(base.totals) && !hasActivity(comparison.compare.totals)) return <AsyncState kind="empty" message={localize(`Chưa ghi nhận số liệu cho năm ${baseYear} và ${compareYear}.`)} />;
    return <YearComparison base={base} baseYear={baseYear} compareYear={compareYear} {...comparison} />;
  }
  if (!hasActivity(base.totals)) return <AsyncState kind="empty" message={localize(`Chưa ghi nhận số liệu cho năm ${baseYear}.`)} />;
  return <YearOverview report={base} year={baseYear} />;
}
