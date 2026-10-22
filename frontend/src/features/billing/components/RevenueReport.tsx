"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { useCallback, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal } from "@/lib/format";
import { billingApi } from "../api";
import type { RevenueByDeptDTO } from "../types";

interface RequestError {
  message: string;
  correlationId: string | null;
}

interface LastQuery {
  fromDate: string;
  toDate: string;
  departmentId: string;
}

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải báo cáo doanh thu.",
    correlationId: null,
  };
}

export function RevenueReport() {
  const today = new Date().toISOString().slice(0, 10);
  const [fromDate, setFromDate] = useState(today);
  const [toDate, setToDate] = useState(today);
  const [departmentId, setDepartmentId] = useState("");
  const [rows, setRows] = useState<RevenueByDeptDTO[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [lastQuery, setLastQuery] = useState<LastQuery | null>(null);

  const load = useCallback(async (query: LastQuery) => {
    setLastQuery(query);
    setLoading(true);
    setError(null);
    try {
      const result = await billingApi.revenueByDepartment(
        query.fromDate,
        query.toDate,
        query.departmentId || undefined,
      );
      setRows(result);
    } catch (cause: unknown) {
      setError(getRequestError(cause));
      setRows(null);
    } finally {
      setLoading(false);
    }
  }, []);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!fromDate || !toDate) {
      setValidationError("Cần chọn đủ ngày bắt đầu và ngày kết thúc.");
      return;
    }
    if (fromDate > toDate) {
      setValidationError("Ngày bắt đầu không được sau ngày kết thúc.");
      return;
    }
    setValidationError(null);
    void load({ fromDate, toDate, departmentId: departmentId.trim() });
  }

  const totalRevenue = rows?.reduce((sum, row) => sum + Number(row.totalRevenue), 0) ?? 0;

  return (
    <section className="mt-6">
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3 sm:flex-row sm:items-end sm:gap-4">
        <Field label="Từ ngày" htmlFor="revenue-from-date" required>
          <input id="revenue-from-date" required type="date" max={toDate || undefined} value={fromDate} onChange={(event) => setFromDate(event.target.value)} className={controlClassName} />
        </Field>
        <Field label="Đến ngày" htmlFor="revenue-to-date" required>
          <input id="revenue-to-date" required type="date" min={fromDate || undefined} value={toDate} onChange={(event) => setToDate(event.target.value)} className={controlClassName} />
        </Field>
        <Field label="Khoa (tuỳ chọn)" htmlFor="revenue-department-id" hint="Để trống xem mọi khoa.">
          <ReferenceInput kind="department" id="revenue-department-id" value={departmentId} onValueChange={(selectedId) => setDepartmentId(selectedId)} placeholder="UUID khoa" className={controlClassName} />
        </Field>
        <Button type="submit" variant="primary" loading={loading}>Xem báo cáo</Button>
      </form>

      {validationError ? <p role="alert" className="mt-2 text-sm text-danger">{validationError}</p> : null}

      {loading ? <AsyncState kind="loading" message="Đang tải báo cáo doanh thu…" /> : null}

      {!loading && error ? (
        <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => lastQuery && void load(lastQuery)} />
      ) : null}

      {!loading && !error && rows !== null && rows.length === 0 ? (
        <AsyncState kind="empty" message="Không có doanh thu đã thanh toán trong khoảng ngày này." />
      ) : null}

      {!loading && !error && rows && rows.length > 0 ? (
        <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <thead className="border-b border-border bg-surface-muted">
              <tr>
                <th scope="col" className="px-4 py-3">Khoa</th>
                <th scope="col" className="px-4 py-3">Số hóa đơn</th>
                <th scope="col" className="px-4 py-3">Doanh thu</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.departmentId} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 font-mono text-xs"><ReferenceText kind="department" value={row.departmentId} fallback="Khoa" /></td>
                  <td className="px-4 py-3">{row.invoiceCount}</td>
                  <td className="px-4 py-3 font-medium">{formatDecimal(row.totalRevenue)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t border-border bg-surface-muted font-semibold">
                <td className="px-4 py-3">Tổng</td>
                <td className="px-4 py-3">{rows.reduce((sum, row) => sum + row.invoiceCount, 0)}</td>
                <td className="px-4 py-3">{formatDecimal(totalRevenue)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      ) : null}
    </section>
  );
}
