"use client";

import { useCallback, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { reportApi } from "../api";
import type { DailyReportDTO } from "../types";

interface ReportRequest {
  date: string;
  departmentId?: string;
}

interface RequestError {
  message: string;
  correlationId: string | null;
}

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return {
      message: cause.message,
      correlationId: cause.correlationId,
    };
  }

  return {
    message: cause instanceof Error ? cause.message : "Không thể tải báo cáo.",
    correlationId: null,
  };
}

export function DailyReportView() {
  const router = useRouter();
  const [date, setDate] = useState("");
  const [departmentId, setDepartmentId] = useState("");
  const [report, setReport] = useState<DailyReportDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [lastRequest, setLastRequest] = useState<ReportRequest | null>(null);

  const loadReport = useCallback(
    async (request: ReportRequest) => {
      setLastRequest(request);
      setLoading(true);
      setError(null);
      setReport(null);

      try {
        const result = await reportApi.daily(request.date, request.departmentId);
        setReport(result);
      } catch (cause: unknown) {
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }

        setError(getRequestError(cause));
      } finally {
        setLoading(false);
      }
    },
    [router],
  );

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const normalizedDate = date.trim();
    const normalizedDepartmentId = departmentId.trim();

    if (!normalizedDate) {
      setValidationError("Ngày báo cáo là bắt buộc.");
      return;
    }

    if (normalizedDepartmentId && !isUuid(normalizedDepartmentId)) {
      setValidationError("Mã khoa phải là UUID hợp lệ.");
      return;
    }

    const request: ReportRequest = {
      date: normalizedDate,
      ...(normalizedDepartmentId
        ? { departmentId: normalizedDepartmentId }
        : {}),
    };

    setValidationError(null);
    setSubmitted(true);
    void loadReport(request);
  }

  return (
    <section className="mt-6">
      <form
        onSubmit={onSubmit}
        noValidate
        className="grid max-w-3xl gap-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] md:items-end"
      >
        <div>
          <label htmlFor="report-date" className="mb-1 block text-sm font-medium">
            Ngày báo cáo
          </label>
          <input
            id="report-date"
            type="date"
            value={date}
            required
            onChange={(event) => {
              setDate(event.target.value);
              setValidationError(null);
            }}
            aria-invalid={validationError && !date.trim() ? true : undefined}
            aria-describedby={validationError ? "report-form-error" : undefined}
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary md:min-h-10"
          />
        </div>
        <div>
          <label
            htmlFor="report-department-id"
            className="mb-1 block text-sm font-medium"
          >
            Mã khoa (tùy chọn)
          </label>
          <input
            id="report-department-id"
            value={departmentId}
            onChange={(event) => {
              setDepartmentId(event.target.value);
              setValidationError(null);
            }}
            placeholder="Nhập UUID khoa"
            aria-invalid={validationError && date.trim() ? true : undefined}
            aria-describedby={validationError ? "report-form-error" : undefined}
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 font-mono text-sm text-foreground placeholder:font-sans placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary md:min-h-10"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="min-h-12 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-colors hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 md:min-h-10"
        >
          Xem báo cáo
        </button>
      </form>

      {validationError ? (
        <p id="report-form-error" role="alert" className="mt-2 text-sm text-danger">
          {validationError}
        </p>
      ) : null}

      {!submitted && !validationError ? (
        <AsyncState kind="idle" message="Chọn ngày để xem báo cáo hàng ngày." />
      ) : null}

      {loading ? <AsyncState kind="loading" message="Đang tải báo cáo…" /> : null}

      {!loading && error ? (
        <AsyncState
          kind="error"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={() => {
            if (lastRequest) {
              void loadReport(lastRequest);
            }
          }}
        />
      ) : null}

      {!loading && !error && report ? (
        <section className="mt-6 rounded-xl border border-border bg-surface p-5">
          <div className="flex flex-col gap-3 border-b border-border pb-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold">Báo cáo ngày {formatLocalDate(report.reportDate)}</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Phạm vi: {report.departmentId ? report.departmentId : "Toàn bệnh viện"}
              </p>
            </div>
            <StatusBadge tone="success">Đã tải</StatusBadge>
          </div>

          <dl className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <div className="rounded-lg border border-border bg-surface-muted p-4">
              <dt className="text-sm text-muted-foreground">Lượt khám</dt>
              <dd className="mt-1 text-2xl font-semibold">{report.visitCount}</dd>
            </div>
            <div className="rounded-lg border border-border bg-surface-muted p-4">
              <dt className="text-sm text-muted-foreground">Xét nghiệm</dt>
              <dd className="mt-1 text-2xl font-semibold">{report.labCount}</dd>
            </div>
            <div className="rounded-lg border border-border bg-surface-muted p-4">
              <dt className="text-sm text-muted-foreground">Đơn thuốc</dt>
              <dd className="mt-1 text-2xl font-semibold">{report.prescriptionCount}</dd>
            </div>
            <div className="rounded-lg border border-border bg-surface-muted p-4">
              <dt className="text-sm text-muted-foreground">Doanh thu</dt>
              <dd className="mt-1 text-2xl font-semibold">{formatDecimal(report.revenue)}</dd>
            </div>
          </dl>
        </section>
      ) : null}
    </section>
  );
}
