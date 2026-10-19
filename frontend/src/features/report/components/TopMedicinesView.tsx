"use client";

import { useCallback, useId, useRef, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { formatLocalDate } from "@/lib/format";
import { reportApi } from "../api";
import { useReportRequest } from "../useReportRequest";
import { isReportDate, localReportDate, validateTopMedicinesQuery, type TopMedicinesQuery } from "../validation";
import { ReportRequestFeedback } from "./ReportRequestFeedback";
import { ReportSessionBoundary } from "./ReportSessionBoundary";

interface TopMedicinesViewProps {
  initialQuery?: TopMedicinesQuery;
  autoLoad?: boolean;
  onQuerySubmit?: (query: TopMedicinesQuery) => boolean;
}

export function TopMedicinesView({ initialQuery, autoLoad = false, onQuerySubmit }: TopMedicinesViewProps) {
  return <ReportSessionBoundary><TopMedicinesContent initialQuery={initialQuery} autoLoad={autoLoad} onQuerySubmit={onQuerySubmit} /></ReportSessionBoundary>;
}

function TopMedicinesContent({ initialQuery, autoLoad = false, onQuerySubmit }: TopMedicinesViewProps) {
  const [defaults] = useState<TopMedicinesQuery>(() => ({ fromDate: localReportDate(), toDate: localReportDate(), limit: 10 }));
  const initial = initialQuery ?? defaults;
  const { fromDate: initialFrom, toDate: initialTo, limit: initialLimit, departmentId: initialDepartment } = initial;
  const [fromDate, setFromDate] = useState(initialFrom);
  const [toDate, setToDate] = useState(initialTo);
  const [departmentId, setDepartmentId] = useState(initialDepartment ?? "");
  const [limit, setLimit] = useState(Number.isFinite(initialLimit) ? String(initialLimit) : "");
  const [validationError, setValidationError] = useState<string | null>(null);
  const errorId = useId();
  const form = useRef<HTMLFormElement>(null);
  const initialRequest = useCallback(async () => ({
    items: await reportApi.topMedicines(initialFrom, initialTo, initialLimit, initialDepartment),
    query: { fromDate: initialFrom, toDate: initialTo, limit: initialLimit, departmentId: initialDepartment },
  }), [initialFrom, initialTo, initialLimit, initialDepartment]);
  const { state, load, retry, clear } = useReportRequest(initialRequest, autoLoad && validateTopMedicinesQuery(initial) === null);
  const loading = state.status === "loading";

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = { fromDate, toDate, limit: Number(limit), departmentId: departmentId.trim() || undefined };
    const error = validateTopMedicinesQuery(query);
    setValidationError(error);
    if (error) {
      clear();
      const selector = !isReportDate(query.fromDate) ? "#medicine-from"
        : !isReportDate(query.toDate) || query.fromDate > query.toDate ? "#medicine-to"
          : !Number.isInteger(query.limit) || query.limit < 1 || query.limit > 50 ? "#medicine-limit" : "#medicine-department";
      form.current?.querySelector<HTMLInputElement>(selector)?.focus();
      return;
    }
    if (onQuerySubmit?.(query)) return;
    load(async () => ({ items: await reportApi.topMedicines(query.fromDate, query.toDate, query.limit, query.departmentId), query }));
  }

  return (
    <section className="mt-6" aria-label="Thuốc xuất nhiều" aria-busy={loading}>
      <form ref={form} onSubmit={submit} noValidate className="grid gap-4 rounded-xl border border-border bg-surface p-4 sm:grid-cols-2 sm:p-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.4fr)_minmax(0,0.6fr)_auto] xl:items-end">
        <Field label="Từ ngày" htmlFor="medicine-from">
          <input id="medicine-from" type="date" value={fromDate} onChange={(event) => { setFromDate(event.target.value); setValidationError(null); }} aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
        </Field>
        <Field label="Đến ngày" htmlFor="medicine-to">
          <input id="medicine-to" type="date" value={toDate} onChange={(event) => { setToDate(event.target.value); setValidationError(null); }} aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
        </Field>
        <Field label="Mã khoa (tùy chọn)" htmlFor="medicine-department">
          <input id="medicine-department" value={departmentId} onChange={(event) => { setDepartmentId(event.target.value); setValidationError(null); }} placeholder="Nhập UUID khoa" aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
        </Field>
        <Field label="Số kết quả" htmlFor="medicine-limit">
          <input id="medicine-limit" type="number" min={1} max={50} step={1} value={limit} onChange={(event) => { setLimit(event.target.value); setValidationError(null); }} aria-describedby={validationError ? errorId : undefined} className={controlClassName} />
        </Field>
        <Button type="submit" variant="primary" loading={loading}>Xem xếp hạng</Button>
        {validationError && <p id={errorId} role="alert" className="text-sm text-danger sm:col-span-2 xl:col-span-5">{validationError}</p>}
      </form>
      <ReportRequestFeedback state={state} idle="Chọn khoảng ngày để xem xếp hạng thuốc." loading="Đang tải xếp hạng thuốc…" onRetry={retry} />
      {state.status === "success" && (
        <section className="mt-6 rounded-xl border border-border bg-surface p-4 sm:p-5">
          <h2 className="text-lg font-semibold">Thuốc xuất nhiều nhất</h2>
          <p className="mt-1 text-sm text-muted-foreground">Từ {formatLocalDate(state.data.query.fromDate)} đến {formatLocalDate(state.data.query.toDate)} · Tối đa {state.data.query.limit} kết quả</p>
          <p className="mt-1 break-all text-xs text-muted-foreground">Phạm vi: {state.data.query.departmentId ?? "Toàn bệnh viện"}</p>
          {state.data.items.length === 0 ? <AsyncState kind="empty" message="Không có dữ liệu xuất thuốc trong khoảng đã chọn." /> : (
            <ol className="mt-5 divide-y divide-border rounded-lg border border-border">
              {state.data.items.map((item, index) => (
                <li key={item.drugId} className="flex items-start gap-3 px-3 py-4 sm:gap-4 sm:px-4">
                  <span aria-label={`Hạng ${index + 1}`} className="grid size-8 shrink-0 place-items-center rounded-full bg-primary/10 text-sm font-semibold text-primary">{index + 1}</span>
                  <div className="min-w-0 flex-1">
                    <p className="break-words font-medium">{item.drugName}</p>
                    <p className="mt-1 break-all font-mono text-xs text-muted-foreground">{item.drugId}</p>
                  </div>
                  <div className="shrink-0 text-right">
                    <strong className="tabular-nums">{item.totalQuantity.toLocaleString("vi-VN")}</strong>
                    <p className="text-xs text-muted-foreground">Số lượng</p>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </section>
      )}
    </section>
  );
}
