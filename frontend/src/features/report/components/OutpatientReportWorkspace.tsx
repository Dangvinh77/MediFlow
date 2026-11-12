"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useState, useSyncExternalStore, type ReactNode } from "react";
import { PageShell } from "@/components/layout/PageShell";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { formatLocalDate } from "@/lib/format";
import { buildOutpatientQuery, readOutpatientQuery } from "../outpatientQuery";
import { dashboardParams } from "../dashboardQuery";
import { OutpatientReportView } from "./OutpatientReportView";
import { ReportDashboard } from "./ReportDashboard";

function ReportDisclosure({
  initiallyOpen,
  title,
  description,
  hint,
  children,
}: {
  initiallyOpen: boolean;
  title: string;
  description: string;
  hint: string;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(initiallyOpen);
  return (
    <details
      open={open}
      onToggle={(event) => setOpen(event.currentTarget.open)}
      className="mt-10 rounded-xl border border-border bg-surface"
    >
      <summary className="cursor-pointer rounded-xl px-4 py-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:px-5">
        <span role="heading" aria-level={2} className="block text-lg font-semibold"><L10nValue>{title}</L10nValue></span>
        <span className="mt-1 block text-sm text-muted-foreground"><L10nValue>{description}</L10nValue></span>
        <span className="mt-1 block text-xs text-muted-foreground"><L10nValue>{hint}</L10nValue></span>
      </summary>
      <L10nValue>{children}</L10nValue>
    </details>
  );
}

export function OutpatientReportWorkspace({ enabled = false }: { enabled?: boolean }) {
  const { localize } = useLocale();
  if (!enabled) return (
    <PageShell title={localize("Báo cáo")}>
      <p role="status" className="mt-6 rounded-xl border border-border bg-surface p-5 text-muted-foreground"><L10n>{"Báo cáo ngoại trú chưa sẵn sàng. Vui lòng liên hệ quản trị viên."}</L10n></p>
      <Link href="/" className="mt-4 inline-flex min-h-11 items-center text-primary underline"><L10n>{"Về trang chủ"}</L10n></Link>
    </PageShell>
  );
  return <EnabledOutpatientWorkspace />;
}

function EnabledOutpatientWorkspace() {
  const { localize } = useLocale();
  const params = useSearchParams();
  const pathname = usePathname();
  // Only ADMIN still reaches the reconciled report; a department head reads the yearly dashboard.
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const reportParams = new URLSearchParams(params.toString());
  dashboardParams.forEach(name => reportParams.delete(name));
  const dashboardQuery = new URLSearchParams(params.toString());
  const rawQuery = reportParams.toString();
  const { query, explicit, autoLoad } = readOutpatientQuery(new URLSearchParams(rawQuery));
  const periodSummary = explicit
    ? query.mode === "daily"
      ? `${formatLocalDate(query.fromDate)} – ${formatLocalDate(query.toDate)}`
      : `Năm ${query.year}`
    : "Kỳ gần nhất có dữ liệu";
  function navigate(next: string) {
    if (next === rawQuery) return false;
    const merged = new URLSearchParams(next);
    dashboardParams.forEach(name => { const value = dashboardQuery.get(name); if (value) merged.set(name, value); });
    window.history.pushState(null, "", `${pathname}?${merged}`);
    return true;
  }
  return (
    <PageShell title={localize("Báo cáo")} description={localize("Doanh thu, lượt khám và cơ cấu dịch vụ theo năm; đổi năm để xem biểu đồ tương ứng hoặc so sánh hai năm.")}>
      <ReportDashboard />
      <L10nValue>{role === "ADMIN" && <>
      <ReportDisclosure
        key={rawQuery}
        initiallyOpen={rawQuery.length > 0}
        title={localize("Báo cáo đã đối soát")}
        description={localize(`${query.mode === "daily" ? "Theo ngày" : "Theo tháng"} · ${periodSummary} · ${query.departmentId ? "Theo khoa đã chọn" : explicit ? "Toàn viện" : "Phạm vi theo kỳ khả dụng"}`)}
        hint="Mở để xem báo cáo từng ngày hoặc từng tháng: biểu đồ dòng tiền, lượt khám, xét nghiệm, đơn thuốc và bảng chi tiết"
      >
        <div className="border-t border-border px-4 pb-4 sm:px-5 sm:pb-5">
          <nav aria-label={localize("Kỳ báo cáo ngoại trú")} className="flex flex-wrap gap-x-6 border-b border-border">
            <L10nValue>{(["daily", "monthly"] as const).map((mode) => {
              const next = new URLSearchParams(rawQuery); next.set("view", mode);
              return <a key={mode} href={`${pathname}?${next}`} aria-current={query.mode === mode ? "page" : undefined} onClick={(event) => {
                if (event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
                  event.preventDefault(); navigate(next.toString());
                }
              }} className={`inline-flex min-h-11 items-center border-b-2 px-1 py-3 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${query.mode === mode ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}><L10nValue>{mode === "daily" ? "Theo ngày" : "Theo tháng"}</L10nValue></a>;
            })}</L10nValue>
          </nav>
          <OutpatientReportView key={rawQuery} initialQuery={query} autoLoad={autoLoad} discoverLatest={!explicit} onQuerySubmit={(submitted) => navigate(buildOutpatientQuery(new URLSearchParams(rawQuery), submitted))} />
        </div>
      </ReportDisclosure>
      </>}</L10nValue>
    </PageShell>
  );
}
