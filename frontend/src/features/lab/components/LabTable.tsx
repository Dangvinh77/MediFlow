"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { labApi } from "../api";
import type { LabTestDTO } from "../types";

export function LabTable() {
  const router = useRouter();
  const [tests, setTests] = useState<LabTestDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    labApi
      .search()
      .then((page) => setTests(page.content))
      .catch((cause: unknown) => {
        if (cause instanceof ApiRequestError && (cause.status === 401 || cause.status === 403)) {
          router.replace("/login");
          return;
        }
        setError(cause instanceof Error ? cause.message : "Không thể tải xét nghiệm.");
      })
      .finally(() => setLoading(false));
  }, [router]);

  if (loading) return <p className="mt-6 text-zinc-500">Đang tải xét nghiệm…</p>;
  if (error) return <p className="mt-6 text-red-600">{error}</p>;
  if (tests.length === 0) return <p className="mt-6 text-zinc-500">Chưa có yêu cầu xét nghiệm.</p>;

  return (
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
            <tr key={test.testId} className="border-b border-zinc-100 last:border-0 dark:border-zinc-800">
              <td className="px-4 py-3">{test.requestedDate}</td>
              <td className="px-4 py-3 font-medium">{test.labType}</td>
              <td className="px-4 py-3 font-mono text-xs">{test.patientId}</td>
              <td className="px-4 py-3 font-mono text-xs">{test.recordId}</td>
              <td className="px-4 py-3">{test.status}</td>
              <td className="px-4 py-3">{test.paid ? "Đã thanh toán" : "Chưa thanh toán"}</td>
              <td className="px-4 py-3">{test.conclusion ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
