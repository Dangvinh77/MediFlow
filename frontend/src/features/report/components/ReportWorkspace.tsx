"use client";

import { usePathname, useSearchParams } from "next/navigation";
import { PageShell } from "@/components/layout/PageShell";
import { buildMonthlyReportQuery, getReportView, readMonthlyReportQuery, reportViews, type ReportView } from "../reportQuery";
import { DailyReportView } from "./DailyReportView";
import { MonthlyReportView } from "./MonthlyReportView";
import { TopMedicinesView } from "./TopMedicinesView";

export function ReportWorkspace() {
  const params = useSearchParams();
  const pathname = usePathname();
  const rawQuery = params.toString();
  const view = getReportView(params.get("view"));
  const monthly = readMonthlyReportQuery(new URLSearchParams(rawQuery));

  // These query-only views fetch client data. Next integrates native history with useSearchParams,
  // keeping Back/Forward available without a server-component navigation for every filter.
  function navigateQuery(next: string) {
    if (next === rawQuery) return false;
    window.history.pushState(null, "", `${pathname}?${next}`);
    return true;
  }

  return (
    <PageShell title={reportViews[view].title} description="Số liệu tổng hợp từ Report.">
      <nav aria-label="Loại báo cáo" className="mt-4 flex flex-wrap gap-x-6 border-b border-border">
        {(Object.keys(reportViews) as ReportView[]).map((key) => {
          const next = new URLSearchParams(rawQuery); next.set("view", key);
          return <a key={key} href={`${pathname}?${next}`} onClick={(event) => {
            if (event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
              event.preventDefault(); navigateQuery(next.toString());
            }
          }} aria-current={key === view ? "page" : undefined} className={`inline-flex min-h-11 items-center border-b-2 px-1 py-3 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${key === view ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}>{reportViews[key].label}</a>;
        })}
      </nav>
      {view === "monthly" && <MonthlyReportView key={rawQuery} initialQuery={monthly.query} autoLoad={monthly.autoLoad} onQuerySubmit={(query) => navigateQuery(buildMonthlyReportQuery(new URLSearchParams(rawQuery), query))} />}
      {view === "daily" && <DailyReportView />}
      {view === "medicines" && <TopMedicinesView />}
    </PageShell>
  );
}
