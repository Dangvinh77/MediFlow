"use client";

import { useCallback, useEffect, useRef } from "react";
import { Button } from "@/components/ui/Button";
import { getRole } from "@/lib/auth";
import { formatLocalDate } from "@/lib/format";
import { reportApi } from "../api";
import { useReportRequest } from "../useReportRequest";
import type { OutpatientAvailablePeriodDTO } from "../types";
import { ReportDepartmentName } from "./ReportDepartmentName";
import { ReportRequestFeedback } from "./ReportRequestFeedback";

interface Props {
  departmentId?: string;
  mode: "daily" | "monthly";
  /** Discover on mount and hand the most recently accepted period to `onSelect` once. */
  autoSelectLatest?: boolean;
  onSelect: (period: OutpatientAvailablePeriodDTO, automatic?: boolean) => void;
}

export function OutpatientAvailablePeriods({ departmentId, mode, autoSelectLatest = false, onSelect }: Props) {
  const request = useCallback(() => reportApi.availablePeriods(departmentId), [departmentId]);
  const needsDepartment = getRole() === "MANAGER" && !departmentId;
  const { state, load, retry } = useReportRequest(request, autoSelectLatest && !needsDepartment);
  const periods = state.status === "success"
    ? state.data.filter(item => mode === "daily" || (
      item.fromDate.endsWith("-01-01") && item.toDate === `${item.fromDate.slice(0, 4)}-12-31`
    ))
    : [];
  const latest = periods[0];
  const autoSelected = useRef(false);
  useEffect(() => {
    // The server orders candidates by acceptance, newest first; only accepted coverage is listed.
    if (!autoSelectLatest || autoSelected.current || !latest) return;
    autoSelected.current = true;
    onSelect(latest, true);
  }, [autoSelectLatest, latest, onSelect]);
  return <section aria-label="Kỳ có dữ liệu đã đối soát" className="mt-4 space-y-3 rounded-xl border border-border bg-surface p-4">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h2 className="font-semibold">Kỳ có dữ liệu đã đối soát</h2>
        <p className="mt-1 text-sm text-muted-foreground">Tìm trong các kỳ đối soát gần nhất. Chọn kỳ để điền bộ lọc, rồi bấm xem báo cáo.</p></div>
      <Button type="button" disabled={needsDepartment} loading={state.status === "loading"} onClick={()=>load(request)}>Tìm kỳ có dữ liệu</Button>
    </div>
    {needsDepartment && <p className="text-sm text-muted-foreground">Chọn khoa được phân quyền trước khi tìm kỳ báo cáo.</p>}
    {state.status !== "idle" && <ReportRequestFeedback state={state} idle="" loading="Đang kiểm tra các kỳ đối soát…" onRetry={retry} />}
    {state.status === "success" && (periods.length ? <ul className="divide-y divide-border">
      {periods.map(item => <li key={`${item.coverageId}:${item.fromDate}:${item.toDate}`} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
        <div><p className="font-medium">{formatLocalDate(item.fromDate)} – {formatLocalDate(item.toDate)}</p>
          <p><ReportDepartmentName id={item.departmentId} /> · {item.currency} · {item.zoneId}</p>
          {item.syntheticData && <p className="text-warning">Dữ liệu mô phỏng</p>}</div>
        <Button type="button" onClick={()=>onSelect(item)}>Dùng kỳ này</Button>
      </li>)}
    </ul> : <p role="status" className="text-sm text-muted-foreground">Chưa tìm thấy kỳ phù hợp trong các đối soát gần nhất. Điều này không có nghĩa hoạt động bằng 0; có thể chọn phạm vi khác hoặc cần đối soát nguồn.</p>)}
  </section>;
}
