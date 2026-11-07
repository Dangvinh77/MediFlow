"use client";

import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReferenceText } from "@/components/references/ReferenceText";
import Link from "next/link";
import { useCallback, useEffect, useState, useSyncExternalStore, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { formatDecimal, formatInstant } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { billingApi } from "../api";
import type { PageResult } from "@/lib/types";
import type { PaymentHistoryItem, PaymentMethod } from "../types";

const PAGE_SIZE = 20;

const methodLabels: Record<PaymentMethod, string> = { CASH: "Tiền mặt", TRANSFER: "Chuyển khoản", INSURANCE: "Bảo hiểm" };
const purposeLabels: Record<string, string> = { EXAM: "Phí khám", LAB_TEST: "Xét nghiệm", PRESCRIPTION: "Thuốc" };

interface RequestError { message: string; correlationId: string | null }
interface HistoryRequest { patientId: string | null; page: number }

function requestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) return { message: cause.message, correlationId: cause.correlationId };
  return { message: cause instanceof Error ? cause.message : "Không thể tải lịch sử thu.", correlationId: null };
}

/** Money actually moved, newest first. Opens on every patient; choosing one narrows the list. */
export function PaymentHistoryLookup() {
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const [patientId, setPatientId] = useState("");
  const [request, setRequest] = useState<HistoryRequest>({ patientId: null, page: 0 });
  const [rows, setRows] = useState<PaymentHistoryItem[] | null>(null);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const show = useCallback((result: PageResult<PaymentHistoryItem>) => {
    setRows(result.content);
    setError(null);
    setTotalPages(result.totalPages);
    setTotalElements(result.totalElements);
  }, []);

  const fail = useCallback((cause: unknown) => {
    if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
    setRows([]);
    setError(requestError(cause));
  }, [router]);

  useEffect(() => { billingApi.paymentHistory(null, 0, PAGE_SIZE).then(show).catch(fail); }, [show, fail]);

  function open(next: HistoryRequest) {
    setRequest(next); setRows(null); setError(null);
    billingApi.paymentHistory(next.patientId, next.page, PAGE_SIZE).then(show).catch(fail);
  }

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const selected = patientId.trim();
    if (selected && !isUuid(selected)) { setValidationError("Chọn một bệnh nhân hợp lệ."); return; }
    setValidationError(null);
    open({ patientId: selected || null, page: 0 });
  }

  return (
    <section className="mt-6">
      {role === "ADMIN" ? (
        <div className="mb-4 flex flex-wrap justify-end gap-2">
          <Link href="/billing/revenue" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold">Doanh thu theo khoa</Link>
          <Link href="/billing/admin/outbox" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold">Phát lại outbox</Link>
        </div>
      ) : null}
      <form onSubmit={onSearch} noValidate className="flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="billing-patient-id" className="mb-1 block text-sm font-medium">Bệnh nhân</label>
          <ReferenceInput
            kind="patient"
            id="billing-patient-id"
            value={patientId}
            onValueChange={(selectedId) => { setPatientId(selectedId); setValidationError(null); }}
            placeholder="Để trống để xem tất cả bệnh nhân"
            aria-invalid={validationError ? true : undefined}
            aria-describedby={validationError ? "billing-patient-id-error" : undefined}
            className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div>
        <button type="submit" disabled={rows === null} className="min-h-11 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50">
          Xem lịch sử thu
        </button>
      </form>

      {validationError ? <p id="billing-patient-id-error" role="alert" className="mt-2 text-sm text-danger">{validationError}</p> : null}
      {rows === null ? <AsyncState kind="loading" message="Đang tải lịch sử thu…" /> : null}
      {error ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => open(request)} /> : null}
      {rows !== null && !error && rows.length === 0 ? (
        <AsyncState kind="empty" message={request.patientId ? "Bệnh nhân này chưa có khoản thu nào." : "Chưa có khoản thu nào."} />
      ) : null}

      {rows !== null && !error && rows.length > 0 ? (
        <>
          <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-[44rem] text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">Thời điểm</th>
                  <th scope="col" className="px-4 py-3">Bệnh nhân</th>
                  <th scope="col" className="px-4 py-3">Khoản</th>
                  <th scope="col" className="px-4 py-3 text-right">Số tiền</th>
                  <th scope="col" className="px-4 py-3">Hình thức</th>
                  <th scope="col" className="px-4 py-3">Loại</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const refund = row.transactionType === "REFUND";
                  return (
                    <tr key={row.transactionId} className="border-b border-border align-top last:border-0">
                      <td className="px-4 py-3">{formatInstant(row.completedAt)}</td>
                      <td className="px-4 py-3"><ReferenceText kind="patient" value={row.patientId} fallback="Bệnh nhân" /></td>
                      <td className="px-4 py-3">{row.purpose ? purposeLabels[row.purpose] ?? "Khoản khác" : "Khoản khác"}</td>
                      <td className="px-4 py-3 text-right font-medium tabular-nums">{refund ? "−" : ""}{formatDecimal(row.amount)} {row.currency}</td>
                      <td className="px-4 py-3">{methodLabels[row.paymentMethod as PaymentMethod] ?? "Khác"}</td>
                      <td className="px-4 py-3"><StatusBadge tone={refund ? "warning" : "success"}>{refund ? "Hoàn tiền" : "Đã thu"}</StatusBadge></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination page={request.page} totalPages={totalPages} totalElements={totalElements} loading={false} onPageChange={(page) => open({ ...request, page })} />
        </>
      ) : null}
    </section>
  );
}
