"use client";

import { useCallback, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge, type StatusTone } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { billingApi } from "../api";
import type { InvoiceDTO, PaymentMethod, SagaStatus } from "../types";

const BILLING_PAGE_SIZE = 20;

const paymentMethodLabels: Record<PaymentMethod, string> = {
  CASH: "Tiền mặt",
  TRANSFER: "Chuyển khoản",
  INSURANCE: "Bảo hiểm",
};

const sagaStatusPresentation: Record<
  SagaStatus,
  { label: string; tone: StatusTone }
> = {
  NONE: { label: "Không áp dụng", tone: "neutral" },
  AWAITING_PAYMENT: { label: "Chờ thanh toán", tone: "warning" },
  PAID: { label: "Đã thanh toán", tone: "success" },
  AWAITING_DISPENSE: { label: "Chờ xuất thuốc", tone: "info" },
  COMPLETED: { label: "Hoàn tất", tone: "success" },
  REFUNDED: { label: "Đã hoàn tiền", tone: "neutral" },
};

interface RequestError {
  message: string;
  correlationId: string | null;
}

interface BillingRequest {
  patientId: string;
  page: number;
}

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return {
      message: cause.message,
      correlationId: cause.correlationId,
    };
  }

  return {
    message: cause instanceof Error ? cause.message : "Không thể tải hóa đơn.",
    correlationId: null,
  };
}

function getPaymentMethodLabel(paymentMethod: PaymentMethod | null): string {
  if (paymentMethod === null) return "—";
  return paymentMethodLabels[paymentMethod] ?? "Không xác định";
}

export function InvoiceLookup() {
  const router = useRouter();
  const [patientId, setPatientId] = useState("");
  const [activePatientId, setActivePatientId] = useState("");
  const [invoices, setInvoices] = useState<InvoiceDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState<BillingRequest>({
    patientId: "",
    page: 0,
  });

  const loadInvoices = useCallback(async (id: string, page: number) => {
    setLastRequest({ patientId: id, page });
    setLoading(true);
    setError(null);
    setInvoices([]);

    try {
      const result = await billingApi.byPatient(id, page, BILLING_PAGE_SIZE);
      setInvoices(result.content);
      setPageNumber(result.number);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      setError(getRequestError(cause));
    } finally {
      setLoading(false);
    }
  }, [router]);

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedPatientId = patientId.trim();

    if (!isUuid(normalizedPatientId)) {
      setValidationError("Mã bệnh nhân phải là UUID hợp lệ.");
      return;
    }

    setValidationError(null);
    setSearched(true);
    setActivePatientId(normalizedPatientId);
    void loadInvoices(normalizedPatientId, 0);
  }

  const showTable = !loading && !error && invoices.length > 0;

  return (
    <section className="mt-6">
      <form
        onSubmit={onSearch}
        noValidate
        className="flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-end"
      >
        <div className="min-w-0 flex-1">
          <label htmlFor="billing-patient-id" className="mb-1 block text-sm font-medium">
            Mã bệnh nhân
          </label>
          <input
            id="billing-patient-id"
            value={patientId}
            onChange={(event) => {
              setPatientId(event.target.value);
              setValidationError(null);
            }}
            placeholder="Nhập UUID bệnh nhân"
            aria-invalid={validationError ? true : undefined}
            aria-describedby={validationError ? "billing-patient-id-error" : undefined}
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
          />
        </div>
        <button
          type="submit"
          disabled={loading || !patientId.trim()}
          className="min-h-12 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-colors hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
        >
          Tra cứu hóa đơn
        </button>
      </form>

      {validationError ? (
        <p id="billing-patient-id-error" role="alert" className="mt-2 text-sm text-danger">
          {validationError}
        </p>
      ) : null}

      {!searched && !validationError ? (
        <AsyncState kind="idle" message="Nhập mã bệnh nhân để xem hóa đơn." />
      ) : null}

      {loading ? <AsyncState kind="loading" message="Đang tải hóa đơn…" /> : null}

      {!loading && error ? (
        <AsyncState
          kind="error"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={() => void loadInvoices(lastRequest.patientId, lastRequest.page)}
        />
      ) : null}

      {searched && !loading && !error && invoices.length === 0 ? (
        <AsyncState kind="empty" message="Bệnh nhân chưa có hóa đơn." />
      ) : null}

      {showTable ? (
        <>
          <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-[52rem] text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">Ngày lập</th>
                  <th scope="col" className="px-4 py-3">Mã hóa đơn</th>
                  <th scope="col" className="px-4 py-3">Tổng tiền</th>
                  <th scope="col" className="px-4 py-3">Thanh toán</th>
                  <th scope="col" className="px-4 py-3">Hình thức</th>
                  <th scope="col" className="px-4 py-3">Trạng thái saga</th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((invoice) => {
                  const sagaStatus = sagaStatusPresentation[invoice.sagaStatus] ?? {
                    label: "Không xác định",
                    tone: "neutral" as const,
                  };

                  return (
                    <tr key={invoice.invoiceId} className="border-b border-border align-top last:border-0">
                      <td className="px-4 py-3">{formatLocalDate(invoice.createdDate)}</td>
                      <td className="px-4 py-3 font-mono text-xs">{invoice.invoiceId}</td>
                      <td className="px-4 py-3 font-medium">{formatDecimal(invoice.totalAmount)}</td>
                      <td className="px-4 py-3">
                        <StatusBadge tone={invoice.isPaid ? "success" : "warning"}>
                          {invoice.isPaid ? "Đã thanh toán" : "Chưa thanh toán"}
                        </StatusBadge>
                      </td>
                      <td className="px-4 py-3">{getPaymentMethodLabel(invoice.paymentMethod)}</td>
                      <td className="px-4 py-3">
                        <StatusBadge tone={sagaStatus.tone}>{sagaStatus.label}</StatusBadge>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination
            page={pageNumber}
            totalPages={totalPages}
            totalElements={totalElements}
            loading={loading}
            onPageChange={(page) => void loadInvoices(activePatientId, page)}
          />
        </>
      ) : null}
    </section>
  );
}
