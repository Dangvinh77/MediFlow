"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useState, type ReactNode } from "react";
import { PageShell } from "@/components/layout/PageShell";
import { buildOutpatientQuery, readOutpatientQuery } from "../outpatientQuery";
import { OutpatientReportView } from "./OutpatientReportView";
import { OutpatientStatisticsView } from "./OutpatientStatisticsView";
import { formatLocalDate } from "@/lib/format";

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
        <span role="heading" aria-level={2} className="block text-lg font-semibold">{title}</span>
        <span className="mt-1 block text-sm text-muted-foreground">{description}</span>
        <span className="mt-1 block text-xs text-muted-foreground">{hint}</span>
      </summary>
      {children}
    </details>
  );
}

export function OutpatientReportWorkspace({ enabled = false }: { enabled?: boolean }) {
  if (!enabled) return (
    <PageShell title="Báo cáo ngoại trú">
      <p role="status" className="mt-6 rounded-xl border border-border bg-surface p-5 text-muted-foreground">Báo cáo ngoại trú chưa sẵn sàng. Vui lòng liên hệ quản trị viên.</p>
      <Link href="/reports" className="mt-4 inline-flex min-h-11 items-center text-primary underline">Về trang báo cáo</Link>
    </PageShell>
  );
  return <EnabledOutpatientWorkspace />;
}

function EnabledOutpatientWorkspace() {
  const params = useSearchParams();
  const pathname = usePathname();
  const rawQuery = params.toString();
  const { query, explicit, autoLoad } = readOutpatientQuery(new URLSearchParams(rawQuery));
  const periodSummary = explicit
    ? query.mode === "daily"
      ? `${formatLocalDate(query.fromDate)} – ${formatLocalDate(query.toDate)}`
      : `Năm ${query.year}`
    : "Kỳ gần nhất có dữ liệu";
  function navigate(next: string) {
    if (next === rawQuery) return false;
    window.history.pushState(null, "", `${pathname}?${next}`);
    return true;
  }
  return (
    <PageShell title="Báo cáo ngoại trú" description="Doanh thu khám, xét nghiệm, thuốc và lượt khám theo khoa; báo cáo đã đối soát bên dưới.">
      <Link href="/reports?view=monthly" className="mt-3 inline-flex min-h-11 items-center text-sm text-primary underline">Các báo cáo khác (nguồn cũ)</Link>
      <OutpatientStatisticsView />
      <ReportDisclosure
        key={rawQuery}
        initiallyOpen={rawQuery.length > 0}
        title="Báo cáo đã đối soát"
        description={`${query.mode === "daily" ? "Theo ngày" : "Theo tháng"} · ${periodSummary} · ${query.departmentId ? "Theo khoa đã chọn" : explicit ? "Toàn viện" : "Phạm vi theo kỳ khả dụng"}`}
        hint="Mở để chọn kỳ, lọc khoa và xem trạng thái, nguồn cùng chi tiết"
      >
        <div className="border-t border-border px-4 pb-4 sm:px-5 sm:pb-5">
          <nav aria-label="Kỳ báo cáo ngoại trú" className="flex flex-wrap gap-x-6 border-b border-border">
            {(["daily", "monthly"] as const).map((mode) => {
              const next = new URLSearchParams(rawQuery); next.set("view", mode);
              return <a key={mode} href={`${pathname}?${next}`} aria-current={query.mode === mode ? "page" : undefined} onClick={(event) => {
                if (event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
                  event.preventDefault(); navigate(next.toString());
                }
              }} className={`inline-flex min-h-11 items-center border-b-2 px-1 py-3 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${query.mode === mode ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}>{mode === "daily" ? "Theo ngày" : "Theo tháng"}</a>;
            })}
          </nav>
          <OutpatientReportView key={rawQuery} initialQuery={query} autoLoad={autoLoad} discoverLatest={!explicit} onQuerySubmit={(submitted) => navigate(buildOutpatientQuery(new URLSearchParams(rawQuery), submitted))} />
        </div>
      </ReportDisclosure>
    </PageShell>
  );
}
