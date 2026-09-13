"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Pagination } from "@/components/Pagination";
import { ApiRequestError } from "@/lib/api";
import { labApi, type LabSearchParams } from "../api";
import type { LabTestDTO, LabTestStatus } from "../types";

const LAB_STATUSES: LabTestStatus[] = ["PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"];

export function LabTable() {
  const router = useRouter();
  const [tests, setTests] = useState<LabTestDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [departmentId, setDepartmentId] = useState("");
  const [status, setStatus] = useState<LabTestStatus | "">("");
  const [activeFilters, setActiveFilters] = useState<LabSearchParams>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const handleError = useCallback((cause: unknown) => {
    if (cause instanceof ApiRequestError && (cause.status === 401 || cause.status === 403)) {
      router.replace("/login");
      return;
    }
    setError(cause instanceof Error ? cause.message : "Không thể tải xét nghiệm.");
  }, [router]);

  async function loadTests(page: number, filters: LabSearchParams) {
    setLoading(true);
    setError(null);
    try {
      const result = await labApi.search({ ...filters, page });
      setTests(result.content);
      setPageNumber(result.number);
      setTotalPages(result.totalPages);
    } catch (cause: unknown) {
      handleError(cause);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    labApi
      .search()
      .then((result) => {
        setTests(result.content);
        setPageNumber(result.number);
        setTotalPages(result.totalPages);
      })
      .catch(handleError)
      .finally(() => setLoading(false));
  }, [handleError]);

  function onFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const filters = {
      departmentId: departmentId.trim() || undefined,
      status: status || undefined,
    };
    setActiveFilters(filters);
    void loadTests(0, filters);
  }

  function onReset() {
    setDepartmentId("");
    setStatus("");
    setActiveFilters({});
    void loadTests(0, {});
  }

  return (
    <section className="mt-6">
      <form onSubmit={onFilter} className="flex flex-col gap-3 lg:flex-row">
        <input
          value={departmentId}
          onChange={(event) => setDepartmentId(event.target.value)}
          placeholder="UUID khoa"
          aria-label="UUID khoa"
          className="min-w-0 flex-1 rounded-lg border border-zinc-300 bg-white px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
        />
        <select
          value={status}
          onChange={(event) => setStatus(event.target.value as LabTestStatus | "")}
          aria-label="Trạng thái xét nghiệm"
          className="rounded-lg border border-zinc-300 bg-white px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
        >
          <option value="">Mọi trạng thái</option>
          {LAB_STATUSES.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
        <button
          type="submit"
          disabled={loading}
          className="rounded-lg bg-blue-600 px-4 py-2 font-medium text-white disabled:opacity-50"
        >
          Lọc
        </button>
        <button
          type="button"
          onClick={onReset}
          disabled={loading}
          className="rounded-lg border border-zinc-300 px-4 py-2 font-medium disabled:opacity-50 dark:border-zinc-700"
        >
          Tất cả
        </button>
      </form>

      {loading && <p className="mt-4 text-zinc-500">Đang tải xét nghiệm…</p>}
      {error && <p className="mt-4 text-red-600">{error}</p>}
      {!loading && !error && tests.length === 0 && (
        <p className="mt-4 text-zinc-500">Chưa có yêu cầu xét nghiệm phù hợp.</p>
      )}

      {!error && tests.length > 0 && (
        <>
          <div className="mt-6 overflow-x-auto rounded-xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
            <table className="w-full min-w-4xl text-left text-sm">
              <thead className="border-b border-zinc-200 bg-zinc-100 dark:border-zinc-800 dark:bg-zinc-950">
                <tr>
                  <th className="px-4 py-3">Ngày yêu cầu</th>
                  <th className="px-4 py-3">Loại</th>
                  <th className="px-4 py-3">Bệnh nhân</th>
                  <th className="px-4 py-3">Hồ sơ</th>
                  <th className="px-4 py-3">Trạng thái</th>
                  <th className="px-4 py-3">Thanh toán</th>
                  <th className="px-4 py-3">Kết luận</th>
                </tr>
              </thead>
              <tbody>
                {tests.map((test) => (
                  <tr
                    key={test.testId}
                    className="border-b border-zinc-100 last:border-0 dark:border-zinc-800"
                  >
                    <td className="px-4 py-3">{test.requestedDate}</td>
                    <td className="px-4 py-3 font-medium">{test.labType}</td>
                    <td className="px-4 py-3 font-mono text-xs">{test.patientId}</td>
                    <td className="px-4 py-3 font-mono text-xs">{test.recordId}</td>
                    <td className="px-4 py-3">{test.status}</td>
                    <td className="px-4 py-3">
                      {test.paid ? "Đã thanh toán" : "Chưa thanh toán"}
                    </td>
                    <td className="px-4 py-3">{test.conclusion ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={pageNumber}
            totalPages={totalPages}
            loading={loading}
            onPageChange={(page) => void loadTests(page, activeFilters)}
          />
        </>
      )}
    </section>
  );
}
