"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { PageShell } from "@/components/layout/PageShell";
import { buildOutpatientQuery, readOutpatientQuery } from "../outpatientQuery";
import { OutpatientReportView } from "./OutpatientReportView";

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
  const { query, autoLoad } = readOutpatientQuery(new URLSearchParams(rawQuery));
  function navigate(next: string) {
    if (next === rawQuery) return false;
    window.history.pushState(null, "", `${pathname}?${next}`);
    return true;
  }
  return (
    <PageShell title="Báo cáo ngoại trú" description="Lượt khám, xét nghiệm, cấp thuốc và thu hoàn tiền đã đối soát.">
      <Link href="/reports" className="mt-3 inline-flex min-h-11 items-center text-sm text-primary underline">Các báo cáo khác</Link>
      <nav aria-label="Kỳ báo cáo ngoại trú" className="mt-3 flex flex-wrap gap-x-6 border-b border-border">
        {(["daily", "monthly"] as const).map((mode) => {
          const next = new URLSearchParams(rawQuery); next.set("view", mode);
          return <a key={mode} href={`${pathname}?${next}`} aria-current={query.mode === mode ? "page" : undefined} onClick={(event) => {
            if (event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
              event.preventDefault(); navigate(next.toString());
            }
          }} className={`inline-flex min-h-11 items-center border-b-2 px-1 py-3 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${query.mode === mode ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}>{mode === "daily" ? "Theo ngày" : "Theo tháng"}</a>;
        })}
      </nav>
      <OutpatientReportView key={rawQuery} initialQuery={query} autoLoad={autoLoad} onQuerySubmit={(submitted) => navigate(buildOutpatientQuery(new URLSearchParams(rawQuery), submitted))} />
    </PageShell>
  );
}
