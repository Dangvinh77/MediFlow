"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import Link from "next/link";
import { FinancialStatus } from "./FinancialStatus";
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge, type StatusTone } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { formatLocalDate } from "@/lib/format";
import type { Role } from "@/lib/roles";
import { isUuid } from "@/lib/validation";
import { labApi, type LabSearchParams } from "../api";
import type { LabCareEpisodeType, LabTestDTO, LabTestStatus } from "../types";
import { labQueueHref } from "../navigation";

const LAB_PAGE_SIZE = 20;
const detailRoles: readonly Role[] = ["ADMIN", "DOCTOR", "NURSE", "LAB_TECH"];
const getServerRole = (): Role | null => null;
const LAB_STATUSES: LabTestStatus[] = [
  "PENDING",
  "AWAITING_PAYMENT",
  "READY",
  "IN_PROGRESS",
  "COMPLETED",
  "CANCELLED",
];

const labStatusPresentation: Record<
  LabTestStatus,
  { label: string; tone: StatusTone }
> = {
  PENDING: { label: "Chờ xử lý", tone: "warning" },
  AWAITING_PAYMENT: { label: "Chờ xác nhận thanh toán", tone: "warning" },
  READY: { label: "Sẵn sàng", tone: "info" },
  IN_PROGRESS: { label: "Đang thực hiện", tone: "info" },
  COMPLETED: { label: "Đã hoàn tất", tone: "success" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
};

interface RequestError {
  message: string;
  correlationId: string | null;
}

interface LabRequest {
  page: number;
  filters: LabSearchParams;
}

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return {
      message: cause.message,
      correlationId: cause.correlationId,
    };
  }

  return {
    message: cause instanceof Error ? cause.message : "Không thể tải xét nghiệm.",
    correlationId: null,
  };
}

export function LabTable({ outpatientEnabled = false, initialEpisodeId = "", initialEpisodeType = "OUTPATIENT_VISIT", initialStatus, initialDepartmentId = "", initialPage = 0 }: { outpatientEnabled?: boolean; initialEpisodeId?: string; initialEpisodeType?: LabCareEpisodeType; initialStatus?: LabTestStatus | ""; initialDepartmentId?: string; initialPage?: number }) {
  const router = useRouter();
  const requestSequence = useRef(0);
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const clinicalReader = role === "ADMIN" || role === "DOCTOR" || role === "NURSE";
  const [tests, setTests] = useState<LabTestDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [departmentId, setDepartmentId] = useState(initialDepartmentId);
  const [status, setStatus] = useState<LabTestStatus | "">(initialStatus ?? "");
  const episodeScoped = outpatientEnabled && isUuid(initialEpisodeId);
  const initialFilters = useMemo<LabSearchParams>(() => ({
    ...(episodeScoped ? { episodeType: initialEpisodeType, episodeId: initialEpisodeId } : {}),
    ...(initialDepartmentId ? { departmentId: initialDepartmentId } : {}),
    ...(initialStatus ? { status: initialStatus } : initialStatus === undefined && role === "LAB_TECH" && !episodeScoped ? { status: "READY" as const } : {}),
  }), [episodeScoped, initialEpisodeId, initialEpisodeType, initialDepartmentId, initialStatus, role]);
  const [episodeType, setEpisodeType] = useState<LabCareEpisodeType | "">(initialFilters.episodeType ?? "");
  const [episodeId, setEpisodeId] = useState(initialFilters.episodeId ?? "");
  const [activeFilters, setActiveFilters] = useState<LabSearchParams>(initialFilters);
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState<LabRequest>({
    page: 0,
    filters: initialFilters,
  });

  const loadTests = useCallback(async (page: number, filters: LabSearchParams) => {
    const sequence = ++requestSequence.current;
    setLastRequest({ page, filters });
    setLoading(true);
    setError(null);

    try {
      const result = await labApi.search({
        ...filters,
        page,
        size: LAB_PAGE_SIZE,
      });
      if (sequence !== requestSequence.current) return;
      setTests(result.content);
      setPageNumber(result.number);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
      window.history.replaceState(null, "", labQueueHref(filters, result.number));
    } catch (cause: unknown) {
      if (sequence !== requestSequence.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      setError(getRequestError(cause));
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    const initialLoad = window.setTimeout(() => {
      setStatus(initialFilters.status ?? "");
      setActiveFilters(initialFilters);
      void loadTests(initialPage, initialFilters);
    }, 0);

    return () => {
      window.clearTimeout(initialLoad);
      requestSequence.current += 1;
    };
  }, [loadTests, initialFilters, initialPage]);

  function onFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedDepartmentId = departmentId.trim();

    if (normalizedDepartmentId && !isUuid(normalizedDepartmentId)) {
      setValidationError("Mã khoa phải là UUID hợp lệ.");
      return;
    }
    const normalizedEpisodeId = episodeId.trim();
    if (normalizedEpisodeId && !isUuid(normalizedEpisodeId)) {
      setValidationError("Mã đợt chăm sóc phải là UUID hợp lệ.");
      return;
    }
    if ((episodeType && !normalizedEpisodeId) || (!episodeType && normalizedEpisodeId)) {
      setValidationError("Loại và mã đợt chăm sóc phải được nhập cùng nhau.");
      return;
    }

    setValidationError(null);
    const filters: LabSearchParams = {
      departmentId: normalizedDepartmentId || undefined,
      status: status || undefined,
      episodeType: episodeType || undefined,
      episodeId: normalizedEpisodeId || undefined,
    };
    setActiveFilters(filters);
    void loadTests(0, filters);
  }

  function onReset() {
    setDepartmentId("");
    setStatus("");
    setEpisodeType(episodeScoped ? initialEpisodeType : "");
    setEpisodeId(episodeScoped ? initialEpisodeId : "");
    setValidationError(null);
    const filters: LabSearchParams = episodeScoped ? { episodeType: initialEpisodeType, episodeId: initialEpisodeId } : {};
    setActiveFilters(filters);
    void loadTests(0, filters);
  }

  return (
    <section className="mt-6">
      {role === "ADMIN" || role === "DOCTOR" ? (
        <div className="mb-5 flex justify-end">
          <Link href={outpatientEnabled ? "/records" : "/lab/new"} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90">
            Tạo yêu cầu xét nghiệm
          </Link>
        </div>
      ) : null}
      {episodeScoped ? <p className="mb-4 border-l-2 border-primary pl-3 text-sm text-muted-foreground">Xét nghiệm thuộc đợt chăm sóc đang xem. Bộ lọc giữ nguyên đợt chăm sóc khi chọn tất cả trạng thái.</p> : null}
      <form onSubmit={onFilter} className="flex flex-wrap items-end gap-3 rounded-xl border border-border bg-surface p-4">
        {clinicalReader ? <div className="min-w-0 flex-1">
          <label htmlFor="lab-department" className="mb-1 block text-sm font-medium">
            Khoa yêu cầu
          </label>
          <ReferenceInput kind="department"
            id="lab-department"
            value={departmentId}
            onValueChange={(selectedId) => setDepartmentId(selectedId)}
            placeholder="Chọn khoa yêu cầu"
            aria-describedby={validationError ? "lab-filter-error" : undefined}
            aria-invalid={validationError ? true : undefined}
            className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div> : null}
        <div>
          <label htmlFor="lab-status" className="mb-1 block text-sm font-medium">
            Trạng thái
          </label>
          <select
            id="lab-status"
            value={status}
            onChange={(event) => setStatus(event.target.value as LabTestStatus | "")}
            className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 py-2 text-sm text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <option value="">Mọi trạng thái</option>
            {LAB_STATUSES.map((value) => (
              <option key={value} value={value}>
                {labStatusPresentation[value].label}
              </option>
            ))}
          </select>
        </div>
        <details className="order-last w-full border-t border-border pt-2">
        <summary className="cursor-pointer py-2 text-sm text-muted-foreground">Tra cứu nâng cao và hỗ trợ</summary>
        <div className="grid gap-3 py-3 sm:grid-cols-2">
        {!clinicalReader ? <div><label htmlFor="lab-department-support" className="mb-1 block text-sm font-medium">Mã khoa</label><ReferenceInput kind="department" id="lab-department-support" value={departmentId} onValueChange={setDepartmentId} /></div> : null}
        <div>
          <label htmlFor="lab-episode-type" className="mb-1 block text-sm font-medium">Loại đợt</label>
          <select
            id="lab-episode-type"
            disabled={episodeScoped}
            value={episodeType}
            onChange={(event) => setEpisodeType(event.target.value as LabCareEpisodeType | "")}
            className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 py-2 text-sm text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <option value="">Mọi loại</option>
            <option value="OUTPATIENT_VISIT">Ngoại trú</option>
            <option value="ADMISSION">Nội trú</option>
          </select>
        </div>
        <div className="min-w-0 flex-1">
          <label htmlFor="lab-episode-id" className="mb-1 block text-sm font-medium">Mã đợt chăm sóc</label>
          <input
            id="lab-episode-id"
            readOnly={episodeScoped}
            value={episodeId}
            onChange={(event) => setEpisodeId(event.target.value)}
            placeholder="UUID đợt chăm sóc"
            aria-describedby={validationError ? "lab-filter-error" : undefined}
            aria-invalid={validationError ? true : undefined}
            className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div>
        </div>
        </details>
        <button
          type="submit"
          disabled={loading}
          className="min-h-11 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50"
        >
          Lọc
        </button>
        <button
          type="button"
          onClick={onReset}
          disabled={loading}
          className="min-h-11 rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50"
        >
          Tất cả
        </button>
      </form>

      {validationError ? (
        <p id="lab-filter-error" role="alert" className="mt-2 text-sm text-danger">
          {validationError}
        </p>
      ) : null}

      {loading && tests.length === 0 ? (
        <AsyncState kind="loading" message="Đang tải xét nghiệm…" />
      ) : null}

      {!loading && error ? (
        <AsyncState
          kind="error"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={() => void loadTests(lastRequest.page, lastRequest.filters)}
        />
      ) : null}

      {!loading && !error && tests.length === 0 ? (
        <AsyncState kind="empty" message="Chưa có yêu cầu xét nghiệm phù hợp." />
      ) : null}

      {!error && tests.length > 0 ? (
        <>
          <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
            <table className={`w-full text-left text-sm ${clinicalReader ? "min-w-4xl" : "min-w-2xl"}`}>
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">Ngày yêu cầu</th>
                  <th scope="col" className="px-4 py-3">Loại</th>
                  <th scope="col" className="px-4 py-3">Bệnh nhân</th>
                  {clinicalReader ? <th scope="col" className="px-4 py-3">Hồ sơ</th> : null}
                  <th scope="col" className="px-4 py-3">Trạng thái</th>
                  <th scope="col" className="px-4 py-3">Thanh toán</th>
                  <th scope="col" className="px-4 py-3">Kết luận</th>
                </tr>
              </thead>
              <tbody>
                {tests.map((test) => {
                  const lifecycle = labStatusPresentation[test.status] ?? {
                    label: "Không xác định",
                    tone: "neutral" as const,
                  };

                  return (
                    <tr
                      key={test.testId}
                      className="border-b border-border align-top last:border-0"
                    >
                      <td className="px-4 py-3">{formatLocalDate(test.requestedDate)}</td>
                      <td className="px-4 py-3 font-medium">
                        {role !== null && detailRoles.includes(role) ? (
                          <Link
                            href={`/lab/${test.testId}?returnTo=${encodeURIComponent(labQueueHref(lastRequest.filters, lastRequest.page))}`}
                            className="text-primary underline-offset-4 hover:underline"
                          >
                            {test.labType}
                          </Link>
                        ) : test.labType}
                      </td>
                      <td className="px-4 py-3"><ReferenceText kind="patient" value={test.patientId} fallback="Bệnh nhân" /></td>
                      {clinicalReader ? <td className="px-4 py-3 text-muted-foreground"><ReferenceText kind="record" value={test.recordId} fallback="Hồ sơ khám" /></td> : null}
                      <td className="px-4 py-3">
                        <StatusBadge tone={lifecycle.tone}>{lifecycle.label}</StatusBadge>
                      </td>
                      <td className="px-4 py-3">
                        <FinancialStatus test={test} />
                      </td>
                      <td className="max-w-md whitespace-normal px-4 py-3">
                        {test.conclusion ?? "—"}
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
            onPageChange={(page) => void loadTests(page, activeFilters)}
          />
        </>
      ) : null}
    </section>
  );
}
