"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, useSyncExternalStore, type FormEvent } from "react";
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
  AWAITING_PAYMENT: { label: "Chờ quyền tài chính", tone: "warning" },
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

export function LabTable() {
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const [tests, setTests] = useState<LabTestDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [departmentId, setDepartmentId] = useState("");
  const [status, setStatus] = useState<LabTestStatus | "">("");
  const [episodeType, setEpisodeType] = useState<LabCareEpisodeType | "">("");
  const [episodeId, setEpisodeId] = useState("");
  const [activeFilters, setActiveFilters] = useState<LabSearchParams>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState<LabRequest>({
    page: 0,
    filters: {},
  });

  const loadTests = useCallback(async (page: number, filters: LabSearchParams) => {
    setLastRequest({ page, filters });
    setLoading(true);
    setError(null);

    try {
      const result = await labApi.search({
        ...filters,
        page,
        size: LAB_PAGE_SIZE,
      });
      setTests(result.content);
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

  useEffect(() => {
    const initialLoad = window.setTimeout(() => {
      void loadTests(0, {});
    }, 0);

    return () => window.clearTimeout(initialLoad);
  }, [loadTests]);

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
    setEpisodeType("");
    setEpisodeId("");
    setValidationError(null);
    setActiveFilters({});
    void loadTests(0, {});
  }

  return (
    <section className="mt-6">
      {role === "ADMIN" || role === "DOCTOR" ? (
        <div className="mb-5 flex justify-end">
          <Link href="/lab/new" className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90">
            Tạo yêu cầu xét nghiệm
          </Link>
        </div>
      ) : null}
      <form onSubmit={onFilter} className="flex flex-col gap-3 lg:flex-row lg:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="lab-department" className="mb-1 block text-sm font-medium">
            Mã khoa
          </label>
          <input
            id="lab-department"
            value={departmentId}
            onChange={(event) => setDepartmentId(event.target.value)}
            placeholder="UUID khoa"
            aria-describedby={validationError ? "lab-filter-error" : undefined}
            aria-invalid={validationError ? true : undefined}
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
          />
        </div>
        <div>
          <label htmlFor="lab-status" className="mb-1 block text-sm font-medium">
            Trạng thái
          </label>
          <select
            id="lab-status"
            value={status}
            onChange={(event) => setStatus(event.target.value as LabTestStatus | "")}
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
          >
            <option value="">Mọi trạng thái</option>
            {LAB_STATUSES.map((value) => (
              <option key={value} value={value}>
                {labStatusPresentation[value].label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="lab-episode-type" className="mb-1 block text-sm font-medium">Loại đợt</label>
          <select
            id="lab-episode-type"
            value={episodeType}
            onChange={(event) => setEpisodeType(event.target.value as LabCareEpisodeType | "")}
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
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
            value={episodeId}
            onChange={(event) => setEpisodeId(event.target.value)}
            placeholder="UUID đợt chăm sóc"
            aria-describedby={validationError ? "lab-filter-error" : undefined}
            aria-invalid={validationError ? true : undefined}
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="min-h-12 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-colors hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
        >
          Lọc
        </button>
        <button
          type="button"
          onClick={onReset}
          disabled={loading}
          className="min-h-12 rounded-lg border border-border bg-surface px-4 py-2 font-medium text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
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
            <table className="w-full min-w-4xl text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">Ngày yêu cầu</th>
                  <th scope="col" className="px-4 py-3">Loại</th>
                  <th scope="col" className="px-4 py-3">Bệnh nhân</th>
                  <th scope="col" className="px-4 py-3">Hồ sơ</th>
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
                            href={`/lab/${test.testId}`}
                            className="text-primary underline-offset-4 hover:underline"
                          >
                            {test.labType}
                          </Link>
                        ) : test.labType}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs">{test.patientId}</td>
                      <td className="px-4 py-3 font-mono text-xs">{test.recordId}</td>
                      <td className="px-4 py-3">
                        <StatusBadge tone={lifecycle.tone}>{lifecycle.label}</StatusBadge>
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge tone={test.paid ? "success" : "warning"}>
                          {test.paid ? "Đã thanh toán" : "Chưa thanh toán"}
                        </StatusBadge>
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
