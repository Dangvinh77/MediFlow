"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Pagination } from "@/components/ui/Pagination";
import { ApiRequestError } from "@/lib/api";
import { patientApi } from "../api";
import type { PatientDTO } from "../types";

export function PatientTable() {
  const router = useRouter();
  const [patients, setPatients] = useState<PatientDTO[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const handleError = useCallback(
    (cause: unknown) => {
      if (
        cause instanceof ApiRequestError &&
        (cause.status === 401 || cause.status === 403)
      ) {
        router.replace("/login");
        return;
      }
      setError(cause instanceof Error ? cause.message : "Không thể tải bệnh nhân.");
    },
    [router],
  );

  const loadPatients = useCallback(
    async (page: number) => {
      setLoading(true);
      setError(null);
      try {
        const result = await patientApi.search({ page });
        setPatients(result.content);
        setPageNumber(result.number);
        setTotalPages(result.totalPages);
      } catch (cause: unknown) {
        handleError(cause);
      } finally {
        setLoading(false);
      }
    },
    [handleError],
  );

  useEffect(() => {
    patientApi
      .search()
      .then((result) => {
        setPatients(result.content);
        setPageNumber(result.number);
        setTotalPages(result.totalPages);
      })
      .catch(handleError)
      .finally(() => setLoading(false));
  }, [handleError]);

  return (
    <section className="mt-6">
      {loading && <p className="text-zinc-500">Đang tải bệnh nhân…</p>}
      {error && <p className="text-red-600">{error}</p>}
      {!loading && !error && patients.length === 0 && (
        <p className="text-zinc-500">Chưa có bệnh nhân nào.</p>
      )}

      {!error && patients.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
            <table className="w-full min-w-3xl text-left text-sm">
              <thead className="border-b border-zinc-200 bg-zinc-100 dark:border-zinc-800 dark:bg-zinc-950">
                <tr>
                  <th className="px-4 py-3">Họ tên</th>
                  <th className="px-4 py-3">Ngày sinh</th>
                  <th className="px-4 py-3">Giới tính</th>
                  <th className="px-4 py-3">Số CMND</th>
                  <th className="px-4 py-3">Điện thoại</th>
                </tr>
              </thead>
              <tbody>
                {patients.map((patient) => (
                  <tr
                    key={patient.maBenhNhan}
                    className="border-b border-zinc-100 last:border-0 dark:border-zinc-800"
                  >
                    <td className="px-4 py-3">{patient.hoTen}</td>
                    <td className="px-4 py-3">{patient.ngaySinh}</td>
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
            onPageChange={(page) => void loadPatients(page)}
          />
        </>
      )}
    </section>
  );
}
