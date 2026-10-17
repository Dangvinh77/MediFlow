"use client";

import { usePathname, useSearchParams } from "next/navigation";
import Link from "next/link";
import { PageShell } from "@/components/layout/PageShell";
import { buildDailyReportQuery, buildMonthlyReportQuery, buildTopMedicinesQuery, getReportView, readDailyReportQuery, readMonthlyReportQuery, readTopMedicinesQuery, reportViews, type ReportView } from "../reportQuery";
import { DailyReportView } from "./DailyReportView";
import { MonthlyReportView } from "./MonthlyReportView";
import { TopMedicinesView } from "./TopMedicinesView";

export function ReportWorkspace({ outpatientEnabled = false }: { outpatientEnabled?: boolean }) {
  const params = useSearchParams();
  const pathname = usePathname();
  const rawQuery = params.toString();
  const view = getReportView(params.get("view"));
  const monthly = readMonthlyReportQuery(new URLSearchParams(rawQuery));
  const daily = readDailyReportQuery(new URLSearchParams(rawQuery));
  const medicines = readTopMedicinesQuery(new URLSearchParams(rawQuery));

  // These query-only views fetch client data. Next integrates native history with useSearchParams,
  // keeping Back/Forward available without a server-component navigation for every filter.
  function navigateQuery(next: string) {
    if (next === rawQuery) return false;
    window.history.pushState(null, "", `${pathname}?${next}`);
    return true;
  }

  return (
    <PageShell title={reportViews[view].title} description="Số liệu tổng hợp từ Report.">
      {outpatientEnabled && <Link href="/reports/outpatient" className="mt-3 inline-flex min-h-11 items-center text-sm font-medium text-primary underline">Báo cáo ngoại trú</Link>}
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
      {view === "daily" && <DailyReportView key={rawQuery} initialQuery={daily.query} autoLoad={daily.autoLoad} onQuerySubmit={(query) => navigateQuery(buildDailyReportQuery(new URLSearchParams(rawQuery), query))} />}
      {view === "medicines" && <TopMedicinesView key={rawQuery} initialQuery={medicines.query} autoLoad={medicines.autoLoad} onQuerySubmit={(query) => navigateQuery(buildTopMedicinesQuery(new URLSearchParams(rawQuery), query))} />}
    </PageShell>
  );
}
