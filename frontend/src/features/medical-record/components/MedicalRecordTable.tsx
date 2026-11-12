"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { ReferenceText } from "@/components/references/ReferenceText";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { FormEvent, useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { formatLocalDate } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { medicalRecordApi } from "../api";
import type { MedicalRecordDTO } from "../types";
import { RecordAccessPurposeForm } from "./RecordAccessPurposeForm";

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

export function MedicalRecordTable({ outpatientEnabled = false }: { outpatientEnabled?: boolean }) {
  const { localize } = useLocale();
  const router = useRouter();
  const requestSequence = useRef(0);
  const mounted = useRef(false);
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const [patientId, setPatientId] = useState("");
  const [activePatientId, setActivePatientId] = useState("");
  const [records, setRecords] = useState<MedicalRecordDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [access, setAccess] = useState<"purpose-required" | "denied" | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const loadRecords = useCallback(async (id: string) => {
    const requestId = ++requestSequence.current;
    setLoading(true);
    setError(null);
    setAccess(null);
    try {
      const result = await medicalRecordApi.byPatient(id);
      if (!mounted.current || requestId !== requestSequence.current) return;
      setRecords(result);
    } catch (cause: unknown) {
      if (!mounted.current || requestId !== requestSequence.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (cause instanceof ApiRequestError && cause.status === 403) {
        setRecords([]);
        setAccess(cause.code === "RECORD_ACCESS_PURPOSE_REQUIRED" ? "purpose-required" : "denied");
        return;
      }
      setError(getRequestError(cause));
    } finally {
      if (mounted.current && requestId === requestSequence.current) setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      requestSequence.current += 1;
    };
  }, []);

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedId = patientId.trim();
    if (!isUuid(normalizedId)) {
      setValidationError("Chọn bệnh nhân từ danh sách kết quả tìm kiếm.");
      return;
    }
    setValidationError(null);
    setSearched(true);
    setActivePatientId(normalizedId);
    void loadRecords(normalizedId);
  }

  return (
    <section className="mt-6">
      <L10nValue>{role === "ADMIN" || role === "DOCTOR" ? (
        <div className="mb-5 flex justify-end">
          <Link href={outpatientEnabled ? "/appointments" : "/records/new"} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90"><L10n>{"Tạo hồ sơ khám"}</L10n></Link>
        </div>
      ) : null}</L10nValue>
      <form onSubmit={onSearch} className="flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="record-patient-id" className="mb-1 block text-sm font-medium"><L10n>{"Bệnh nhân"}</L10n></label>
          <ReferenceInput kind="patient"
            id="record-patient-id"
            value={patientId}
            onValueChange={(selectedId) => setPatientId(selectedId)}
            placeholder={localize("Nhập tên hoặc mã bệnh nhân")}
            aria-describedby={validationError ? "record-patient-id-error" : undefined}
            className="min-h-11 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div>
        <button type="submit" disabled={loading || !patientId.trim()} className="min-h-11 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"><L10n>{"Tìm hồ sơ"}</L10n></button>
      </form>

      <L10nValue>{validationError ? <p id="record-patient-id-error" role="alert" className="mt-2 text-sm text-danger"><L10nValue>{validationError}</L10nValue></p> : null}</L10nValue>
      <L10nValue>{!searched && !validationError ? <AsyncState kind="idle" message={localize("Nhập mã bệnh nhân để xem hồ sơ.")} /> : null}</L10nValue>
      <L10nValue>{loading ? <AsyncState kind="loading" message={localize("Đang tải hồ sơ khám…")} /> : null}</L10nValue>
      <L10nValue>{!loading && error ? <div><AsyncState kind="error" message={localize(error.message)} onRetry={() => void loadRecords(activePatientId)} /><L10nValue>{error.correlationId ? <details className="mt-2 text-xs text-muted-foreground"><summary className="min-h-8 cursor-pointer underline underline-offset-4"><L10n>{"Thông tin hỗ trợ"}</L10n></summary><p className="mt-1 break-all font-mono"><L10n>{"Mã theo dõi: "}</L10n>{error.correlationId}</p></details> : null}</L10nValue></div> : null}</L10nValue>
      <L10nValue>{!loading && access === "denied" ? <p role="status" className="mt-4 text-sm text-muted-foreground"><L10n>{"Bạn không có quyền xem hồ sơ khám của bệnh nhân này."}</L10n></p> : null}</L10nValue>
      <L10nValue>{!loading && access === "purpose-required" ? <section aria-label={localize("Khai báo mục đích xem hồ sơ")} className="mt-4"><p className="text-sm text-muted-foreground"><L10n>{"Bạn cần khai báo mục đích trước khi xem hồ sơ của bệnh nhân này."}</L10n></p><RecordAccessPurposeForm patientId={activePatientId} role={role} onDeclared={() => void loadRecords(activePatientId)} /></section> : null}</L10nValue>
      <L10nValue>{searched && !loading && !error && !access && records.length === 0 ? <AsyncState kind="empty" message={localize("Bệnh nhân chưa có hồ sơ khám.")} /> : null}</L10nValue>

      <L10nValue>{!loading && !error && records.length > 0 ? (
        <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
          <table className="w-full min-w-4xl text-left text-sm">
            <thead className="border-b border-border bg-surface-muted">
              <tr><th scope="col" className="px-4 py-3"><L10n>{"Ngày khám"}</L10n></th><th scope="col" className="px-4 py-3"><L10n>{"Hồ sơ khám"}</L10n></th><th scope="col" className="px-4 py-3"><L10n>{"Bác sĩ"}</L10n></th><th scope="col" className="px-4 py-3"><L10n>{"Triệu chứng"}</L10n></th><th scope="col" className="px-4 py-3"><L10n>{"Chẩn đoán"}</L10n></th></tr>
            </thead>
            <tbody>
              <L10nValue>{records.map((record) => (
                <tr key={record.recordId} className="border-b border-border align-top last:border-0">
                  <td className="px-4 py-3"><L10nValue>{formatLocalDate(record.examinationDate)}</L10nValue></td>
                  <td className="px-4 py-3">
                    <Link
                      href={`/records/${record.recordId}`}
                      className="break-all text-primary underline-offset-4 hover:underline"
                    >
                      <ReferenceText value={record.recordId} fallback={`Hồ sơ ${formatLocalDate(record.examinationDate)}`} technical={false} />
                    </Link>
                  </td>
                  <td className="px-4 py-3"><ReferenceText kind="doctor" value={record.doctorId} fallback="Bác sĩ" /></td>
                  <td className="max-w-xs whitespace-normal px-4 py-3">{record.symptoms}</td>
                  <td className="max-w-md whitespace-normal px-4 py-3">
                    <L10nValue>{record.diagnoses.length > 0
                      ? record.diagnoses.map((diagnosis) => (
                        <div key={diagnosis.diagnosisId} className="mb-2 last:mb-0">
                          <p className="font-medium">{diagnosis.diagnosisName}</p>
                          <L10nValue>{diagnosis.icdCode ? <p className="text-xs text-muted-foreground">ICD: {diagnosis.icdCode}</p> : null}</L10nValue>
                          <L10nValue>{diagnosis.description ? <p className="mt-1 text-muted-foreground"><L10nValue>{diagnosis.description}</L10nValue></p> : null}</L10nValue>
                        </div>
                      ))
                      : "—"}</L10nValue>
                  </td>
                </tr>
              ))}</L10nValue>
            </tbody>
          </table>
        </div>
      ) : null}</L10nValue>
    </section>
  );
}
