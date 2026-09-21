"use client";

import { FormEvent, useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { formatLocalDate } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { medicalRecordApi } from "../api";
import type { MedicalRecordDTO } from "../types";

interface RequestError {
  message: string;
  correlationId: string | null;
}

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải hồ sơ khám.",
    correlationId: null,
  };
}

export function MedicalRecordTable() {
  const router = useRouter();
  const [patientId, setPatientId] = useState("");
  const [activePatientId, setActivePatientId] = useState("");
  const [records, setRecords] = useState<MedicalRecordDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const loadRecords = useCallback(async (id: string) => {
    setLoading(true);
    setError(null);
    try {
      setRecords(await medicalRecordApi.byPatient(id));
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
    const normalizedId = patientId.trim();
    if (!isUuid(normalizedId)) {
      setValidationError("Mã bệnh nhân phải là UUID hợp lệ.");
      return;
    }
    setValidationError(null);
    setSearched(true);
    setActivePatientId(normalizedId);
    void loadRecords(normalizedId);
  }

  return (
    <section className="mt-6">
      <form onSubmit={onSearch} className="flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="record-patient-id" className="mb-1 block text-sm font-medium">Mã bệnh nhân</label>
          <input
            id="record-patient-id"
            value={patientId}
            onChange={(event) => setPatientId(event.target.value)}
            placeholder="Nhập UUID bệnh nhân"
            aria-describedby={validationError ? "record-patient-id-error" : undefined}
            className="min-h-11 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div>
        <button type="submit" disabled={loading || !patientId.trim()} className="min-h-11 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">Tìm hồ sơ</button>
      </form>

      {validationError ? <p id="record-patient-id-error" role="alert" className="mt-2 text-sm text-danger">{validationError}</p> : null}
      {!searched && !validationError ? <AsyncState kind="idle" message="Nhập mã bệnh nhân để xem hồ sơ." /> : null}
      {loading ? <AsyncState kind="loading" message="Đang tải hồ sơ khám…" /> : null}
      {!loading && error ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => void loadRecords(activePatientId)} /> : null}
      {searched && !loading && !error && records.length === 0 ? <AsyncState kind="empty" message="Bệnh nhân chưa có hồ sơ khám." /> : null}

      {!loading && !error && records.length > 0 ? (
        <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
          <table className="w-full min-w-4xl text-left text-sm">
            <thead className="border-b border-border bg-surface-muted">
              <tr><th scope="col" className="px-4 py-3">Ngày khám</th><th scope="col" className="px-4 py-3">Mã hồ sơ</th><th scope="col" className="px-4 py-3">Bác sĩ</th><th scope="col" className="px-4 py-3">Triệu chứng</th><th scope="col" className="px-4 py-3">Chẩn đoán</th></tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.recordId} className="border-b border-border align-top last:border-0">
                  <td className="px-4 py-3">{formatLocalDate(record.examinationDate)}</td>
                  <td className="px-4 py-3 font-mono text-xs">{record.recordId}</td>
                  <td className="px-4 py-3 font-mono text-xs">{record.doctorId}</td>
                  <td className="max-w-xs whitespace-normal px-4 py-3">{record.symptoms}</td>
                  <td className="max-w-md whitespace-normal px-4 py-3">
                    {record.diagnoses.length > 0
                      ? record.diagnoses.map((diagnosis) => (
                        <div key={diagnosis.diagnosisId} className="mb-2 last:mb-0">
                          <p className="font-medium">{diagnosis.diagnosisName}</p>
                          {diagnosis.icdCode ? <p className="text-xs text-muted-foreground">ICD: {diagnosis.icdCode}</p> : null}
                          {diagnosis.description ? <p className="mt-1 text-muted-foreground">{diagnosis.description}</p> : null}
                        </div>
                      ))
                      : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}
