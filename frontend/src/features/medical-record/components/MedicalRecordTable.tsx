"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { medicalRecordApi } from "../api";
import type { MedicalRecordDTO } from "../types";

export function MedicalRecordTable() {
  const router = useRouter();
  const [patientId, setPatientId] = useState("");
  const [records, setRecords] = useState<MedicalRecordDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedId = patientId.trim();
    if (!normalizedId) return;

    setLoading(true);
    setError(null);
    try {
      setRecords(await medicalRecordApi.byPatient(normalizedId));
      setSearched(true);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && (cause.status === 401 || cause.status === 403)) {
        router.replace("/login");
        return;
      }
      setError(cause instanceof Error ? cause.message : "Không thể tải hồ sơ khám.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="mt-6">
      <form onSubmit={onSearch} className="flex max-w-2xl flex-col gap-3 sm:flex-row">
        <input
          value={patientId}
          onChange={(event) => setPatientId(event.target.value)}
          placeholder="Nhập UUID bệnh nhân"
          aria-label="UUID bệnh nhân"
          className="min-w-0 flex-1 rounded-lg border border-zinc-300 bg-white px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
        />
        <button
          type="submit"
          disabled={loading || !patientId.trim()}
          className="rounded-lg bg-blue-600 px-4 py-2 font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
        >
          {loading ? "Đang tìm…" : "Tìm hồ sơ"}
        </button>
      </form>

      {error && <p className="mt-4 text-red-600">{error}</p>}
      {!searched && !error && <p className="mt-4 text-zinc-500">Nhập mã bệnh nhân để xem hồ sơ.</p>}
      {searched && !loading && !error && records.length === 0 && (
        <p className="mt-4 text-zinc-500">Bệnh nhân chưa có hồ sơ khám.</p>
      )}

      {records.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded-xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
          <table className="w-full min-w-4xl text-left text-sm">
            <thead className="border-b border-zinc-200 bg-zinc-100 dark:border-zinc-800 dark:bg-zinc-950">
              <tr>
                <th className="px-4 py-3">Ngày khám</th>
                <th className="px-4 py-3">Mã hồ sơ</th>
                <th className="px-4 py-3">Bác sĩ</th>
                <th className="px-4 py-3">Triệu chứng</th>
                <th className="px-4 py-3">Chẩn đoán</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.recordId} className="border-b border-zinc-100 last:border-0 dark:border-zinc-800">
                  <td className="px-4 py-3">{record.examinationDate}</td>
                  <td className="px-4 py-3 font-mono text-xs">{record.recordId}</td>
                  <td className="px-4 py-3 font-mono text-xs">{record.doctorId}</td>
                  <td className="px-4 py-3">{record.symptoms}</td>
                  <td className="px-4 py-3">
                    {record.diagnoses.length > 0
                      ? record.diagnoses.map((diagnosis) => diagnosis.diagnosisName).join(", ")
                      : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
