"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { ApiRequestError } from "@/lib/api";
import { formatLocalDate } from "@/lib/format";
import { patientApi } from "../api";
import type { PatientDTO } from "../types";

const PATIENT_PAGE_SIZE = 20;

interface PatientRequest {
  keyword: string;
  page: number;
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
    message: cause instanceof Error ? cause.message : "Không thể tải bệnh nhân.",
    correlationId: null,
  };
}

export function PatientTable() {
  const router = useRouter();
  const [patients, setPatients] = useState<PatientDTO[]>([]);
  const [error, setError] = useState<RequestError | null>(null);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [activeKeyword, setActiveKeyword] = useState("");
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState<PatientRequest>({
    keyword: "",
    page: 0,
  });

  const loadPatients = useCallback(async (request: PatientRequest) => {
    setLastRequest(request);
    setLoading(true);
    setError(null);

    try {
      const result = await patientApi.search({
        keyword: request.keyword || undefined,
        page: request.page,
        size: PATIENT_PAGE_SIZE,
      });
      setPatients(result.content);
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
      void loadPatients({ keyword: "", page: 0 });
    }, 0);

    return () => window.clearTimeout(initialLoad);
  }, [loadPatients]);

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const appliedKeyword = keyword.trim();
    setActiveKeyword(appliedKeyword);
    void loadPatients({ keyword: appliedKeyword, page: 0 });
  }

  function onReset() {
    setKeyword("");
    setActiveKeyword("");
    void loadPatients({ keyword: "", page: 0 });
  }

  return (
    <section className="mt-6">
      <form onSubmit={onSearch} className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="patient-keyword" className="mb-1 block text-sm font-medium">
            Từ khóa bệnh nhân
          </label>
          <input
            id="patient-keyword"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="Tên hoặc mã bệnh nhân"
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="min-h-12 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-colors hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
        >
          Tìm kiếm
        </button>
        <button
          type="button"
          onClick={onReset}
          disabled={loading}
          className="min-h-12 rounded-lg border border-border bg-surface px-4 py-2 font-medium text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
        >
          Xóa lọc
        </button>
      </form>

      {loading ? <AsyncState kind="loading" message="Đang tải bệnh nhân…" /> : null}

      {!loading && error ? (
        <AsyncState
          kind="error"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={() => void loadPatients(lastRequest)}
        />
      ) : null}

      {!loading && !error && patients.length === 0 ? (
        <AsyncState kind="empty" message="Chưa có bệnh nhân phù hợp." />
      ) : null}

      {!loading && !error && patients.length > 0 ? (
        <>
          <div className="overflow-x-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-3xl text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">Họ tên</th>
                  <th scope="col" className="px-4 py-3">Ngày sinh</th>
                  <th scope="col" className="px-4 py-3">Giới tính</th>
                  <th scope="col" className="px-4 py-3">Số CMND</th>
                  <th scope="col" className="px-4 py-3">Điện thoại</th>
                </tr>
              </thead>
              <tbody>
                {patients.map((patient) => (
                  <tr
                    key={patient.maBenhNhan}
                    className="border-b border-border last:border-0"
                  >
                    <td className="px-4 py-3">{patient.hoTen}</td>
                    <td className="px-4 py-3">{formatLocalDate(patient.ngaySinh)}</td>
                    <td className="px-4 py-3">{patient.gioiTinh}</td>
                    <td className="px-4 py-3">{patient.soCmnd}</td>
                    <td className="px-4 py-3">{patient.soDienThoai ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            loading={loading}
            page={pageNumber}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={(page) => void loadPatients({ keyword: activeKeyword, page })}
          />
        </>
      ) : null}
    </section>
  );
}
