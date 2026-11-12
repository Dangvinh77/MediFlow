"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { ReferenceText } from "@/components/references/ReferenceText";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { useRouter } from "next/navigation";
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
  const { localize } = useLocale();
  const router = useRouter();
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
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3 sm:flex-row sm:items-start sm:gap-4">
        <Field label={localize("Từ ngày")} htmlFor="revenue-from-date" required>
          <input id="revenue-from-date" required type="date" max={toDate || undefined} value={fromDate} onChange={(event) => setFromDate(event.target.value)} className={controlClassName} />
        </Field>
        <Field label={localize("Đến ngày")} htmlFor="revenue-to-date" required>
          <input id="revenue-to-date" required type="date" min={fromDate || undefined} value={toDate} onChange={(event) => setToDate(event.target.value)} className={controlClassName} />
        </Field>
        <Field label={localize("Khoa (tuỳ chọn)")} htmlFor="revenue-department-id" hint="Để trống xem mọi khoa, hoặc gõ vài chữ rồi bấm 'Tìm' và chọn 1 khoa trong danh sách hiện ra.">
          <ReferenceInput kind="department" id="revenue-department-id" value={departmentId} onValueChange={(selectedId) => setDepartmentId(selectedId)} placeholder="UUID khoa" className={controlClassName} />
        </Field>
        <div className="flex shrink-0 gap-2 sm:mt-[1.625rem]">
          <Button type="submit" variant="primary" loading={loading}><L10n>{"Xem báo cáo"}</L10n></Button>
          <Button type="button" disabled={loading} onClick={() => router.back()}><L10n>{"Quay lại"}</L10n></Button>
        </div>
      </form>

      <L10nValue>{validationError ? <p role="alert" className="mt-2 text-sm text-danger"><L10nValue>{validationError}</L10nValue></p> : null}</L10nValue>

      <L10nValue>{loading ? <AsyncState kind="loading" message={localize("Đang tải báo cáo doanh thu…")} /> : null}</L10nValue>

      <L10nValue>{!loading && error ? (
        <AsyncState kind="error" message={localize(error.message)} correlationId={error.correlationId} onRetry={() => lastQuery && void load(lastQuery)} />
      ) : null}</L10nValue>

      <L10nValue>{!loading && !error && rows !== null && rows.length === 0 ? (
        <AsyncState kind="empty" message={localize("Không có doanh thu đã thanh toán trong khoảng ngày này.")} />
      ) : null}</L10nValue>

      <L10nValue>{!loading && !error && rows && rows.length > 0 ? (
        <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <thead className="border-b border-border bg-surface-muted">
              <tr>
                <th scope="col" className="px-4 py-3">Khoa</th>
                <th scope="col" className="px-4 py-3"><L10n>{"Số hóa đơn"}</L10n></th>
                <th scope="col" className="px-4 py-3">Doanh thu</th>
              </tr>
            </thead>
            <tbody>
              <L10nValue>{rows.map((row) => (
                <tr key={row.departmentId} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 font-mono text-xs"><ReferenceText kind="department" value={row.departmentId} fallback="Khoa" /></td>
                  <td className="px-4 py-3">{row.invoiceCount}</td>
                  <td className="px-4 py-3 font-medium"><L10nValue>{formatDecimal(row.totalRevenue)}</L10nValue></td>
                </tr>
              ))}</L10nValue>
            </tbody>
            <tfoot>
              <tr className="border-t border-border bg-surface-muted font-semibold">
                <td className="px-4 py-3"><L10n>{"Tổng"}</L10n></td>
                <td className="px-4 py-3"><L10nValue>{rows.reduce((sum, row) => sum + row.invoiceCount, 0)}</L10nValue></td>
                <td className="px-4 py-3"><L10nValue>{formatDecimal(totalRevenue)}</L10nValue></td>
              </tr>
            </tfoot>
          </table>
        </div>
      ) : null}</L10nValue>
    </section>
  );
}
