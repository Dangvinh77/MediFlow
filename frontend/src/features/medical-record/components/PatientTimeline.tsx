"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useCallback, useEffect, useRef, useState, useSyncExternalStore, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReferenceText } from "@/components/references/ReferenceText";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { getSessionKey } from "@/lib/session";
import { isUuid } from "@/lib/validation";
import type { PageResult } from "@/lib/types";
import { medicalRecordApi } from "../api";
import type { MedicalRecordStatus, TimelineEntryDTO, TimelineQuery, TimelineVisitKind } from "../types";
import { RecordAccessPurposeForm } from "./RecordAccessPurposeForm";

interface RequestError {
  message: string;
  correlationId: string | null;
}

const visitKindLabels: Record<TimelineVisitKind, string> = {
  NEW: "Khám mới",
  FOLLOW_UP: "Tái khám",
  RETURN: "Quay lại",
};

const statusLabels: Record<MedicalRecordStatus, string> = {
  OPEN: "Đang khám",
  COMPLETED: "Đã hoàn tất",
  CLOSED_WITHOUT_CONCLUSION: "Đã kết thúc không có kết luận",
};

function formatExaminationDate(value: string): string {
  const date = value.length === 10 ? new Date(`${value}T12:00:00+07:00`) : new Date(value);
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(date);
}

function errorFrom(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải dòng thời gian bệnh nhân.",
    correlationId: null,
  };
}

function groupEntries(entries: TimelineEntryDTO[]) {
  const courseIds = [...new Set(entries.flatMap((entry) => entry.treatmentCourseIds ?? []))];
  if (courseIds.length === 0) return [{ key: "all-visits", heading: null, entries }];

  const groups = courseIds.map((courseId, index) => ({
    key: `course-${courseId}`,
    heading: `Quá trình điều trị ${index + 1}`,
    entries: entries.filter((entry) => entry.treatmentCourseIds?.includes(courseId)),
  }));
  const unassigned = entries.filter((entry) => !entry.treatmentCourseIds?.length);
  if (unassigned.length > 0) groups.push({ key: "unassigned-visits", heading: "Lượt chưa gắn vào quá trình", entries: unassigned });
  return groups;
}

export function PatientTimeline({ outpatientEnabled = false }: { outpatientEnabled?: boolean }) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  const identityKey = `${role ?? ""}:${session ?? ""}`;
  return <PatientTimelineSession key={identityKey} role={role} outpatientEnabled={outpatientEnabled} />;
}

function PatientTimelineSession({ role, outpatientEnabled }: { role: ReturnType<typeof getRole>; outpatientEnabled: boolean }) {
  const { localize } = useLocale();
  const router = useRouter();
  const requestSequence = useRef(0);
  const mounted = useRef(false);
  const [patientId, setPatientId] = useState("");
  const [activePatientId, setActivePatientId] = useState("");
  const [departmentId, setDepartmentId] = useState("");
  const [statusFilter, setStatusFilter] = useState<"" | MedicalRecordStatus>("");
  const [appliedFilters, setAppliedFilters] = useState<{ departmentId: string | null; status: MedicalRecordStatus | null }>({ departmentId: null, status: null });
  const [pageNumber, setPageNumber] = useState(0);
  const [result, setResult] = useState<PageResult<TimelineEntryDTO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [accessDenied, setAccessDenied] = useState(false);
  const [purposeRequired, setPurposeRequired] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const load = useCallback(async (
    id: string,
    page: number,
    filters: { departmentId: string | null; status: MedicalRecordStatus | null },
  ) => {
    const requestId = ++requestSequence.current;
    setLoading(true);
    setError(null);
    setAccessDenied(false);
    setPurposeRequired(false);
    const query: TimelineQuery = { page, size: 20, ...filters };
    try {
      const response = await medicalRecordApi.timeline(id, query);
      if (!mounted.current || requestId !== requestSequence.current) return;
      setResult(response);
    } catch (cause) {
      if (!mounted.current || requestId !== requestSequence.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (cause instanceof ApiRequestError && cause.status === 403 && cause.code === "RECORD_ACCESS_PURPOSE_REQUIRED") {
        setResult(null);
        setPurposeRequired(true);
        return;
      }
      if (cause instanceof ApiRequestError && cause.status === 403) {
        setResult(null);
        setAccessDenied(true);
        return;
      }
      setResult(null);
      setError(errorFrom(cause));
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

  function onPatientChange(value: string) {
    if (value !== patientId) {
      requestSequence.current += 1;
      setResult(null);
      setError(null);
      setAccessDenied(false);
      setPurposeRequired(false);
      setLoading(false);
      setSearched(false);
      setActivePatientId("");
    }
    setPatientId(value);
    setValidationError(null);
  }

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const id = patientId.trim();
    if (!isUuid(id)) {
      setValidationError("Chọn bệnh nhân từ danh sách kết quả tìm kiếm.");
      return;
    }
    setValidationError(null);
    setSearched(true);
    setActivePatientId(id);
    setPageNumber(0);
    void load(id, 0, appliedFilters);
  }

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!activePatientId) return;
    const filters = {
      departmentId: departmentId.trim() || null,
      status: statusFilter || null,
    };
    setAppliedFilters(filters);
    setPageNumber(0);
    void load(activePatientId, 0, filters);
  }

  const retry = () => {
    if (activePatientId) void load(activePatientId, pageNumber, appliedFilters);
  };
  const groups = result ? groupEntries(result.content) : [];

  return (
    <section className="mt-6 space-y-5">
      <L10nValue>{(role === "ADMIN" || role === "DOCTOR") ? (
        <div className="flex justify-end">
          <Link href={outpatientEnabled ? "/appointments" : "/records/new"} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90"><L10n>{"Tạo hồ sơ khám"}</L10n></Link>
        </div>
      ) : null}</L10nValue>

      <form onSubmit={onSearch} className="flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="timeline-patient-id" className="mb-1 block text-sm font-medium"><L10n>{"Bệnh nhân"}</L10n></label>
          <ReferenceInput
            kind="patient"
            id="timeline-patient-id"
            value={patientId}
            onValueChange={onPatientChange}
            placeholder={localize("Nhập tên hoặc mã bệnh nhân")}
            aria-describedby={validationError ? "timeline-patient-error" : undefined}
            className="min-h-11 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div>
        <button type="submit" disabled={loading || !patientId.trim()} className="min-h-11 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"><L10n>{"Tìm hồ sơ"}</L10n></button>
      </form>
      <L10nValue>{validationError ? <p id="timeline-patient-error" role="alert" className="text-sm text-danger"><L10nValue>{validationError}</L10nValue></p> : null}</L10nValue>

      <L10nValue>{activePatientId ? (
        <form onSubmit={applyFilters} className="grid gap-3 rounded-xl border border-border bg-surface p-4 sm:grid-cols-[minmax(0,1fr)_minmax(12rem,0.7fr)_auto] sm:items-end">
          <div>
            <label htmlFor="timeline-department" className="mb-1 block text-sm font-medium">Khoa</label>
            <ReferenceInput kind="department" id="timeline-department" value={departmentId} onValueChange={setDepartmentId} placeholder={localize("Tất cả khoa")} className="min-h-10 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm" />
          </div>
          <div>
            <label htmlFor="timeline-status" className="mb-1 block text-sm font-medium"><L10n>{"Trạng thái"}</L10n></label>
            <select id="timeline-status" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as "" | MedicalRecordStatus)} className="min-h-10 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm">
              <option value=""><L10n>{"Tất cả trạng thái"}</L10n></option>
              <L10nValue>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}><L10nValue>{label}</L10nValue></option>)}</L10nValue>
            </select>
          </div>
          <button type="submit" disabled={loading} className="min-h-10 rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted disabled:opacity-50"><L10n>{"Lọc"}</L10n></button>
        </form>
      ) : null}</L10nValue>

      <L10nValue>{!searched && !loading ? <AsyncState kind="idle" message={localize("Nhập mã bệnh nhân để xem dòng thời gian.")} /> : null}</L10nValue>
      <L10nValue>{loading ? <AsyncState kind="loading" message={localize("Đang tải dòng thời gian…")} /> : null}</L10nValue>
      <L10nValue>{!loading && error ? <div><AsyncState kind="error" message={localize(error.message)} onRetry={retry} /><L10nValue>{error.correlationId ? <p className="mt-2 text-xs text-muted-foreground"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{error.correlationId}</span></p> : null}</L10nValue></div> : null}</L10nValue>
      <L10nValue>{!loading && accessDenied ? <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm"><L10n>{"Bạn không có quyền xem hồ sơ của bệnh nhân này"}</L10n></p> : null}</L10nValue>
      <L10nValue>{!loading && purposeRequired && activePatientId ? <section aria-label={localize("Khai báo mục đích xem hồ sơ")} className="rounded-xl border border-border bg-surface p-5"><p className="mb-3 text-sm"><L10n>{"Bạn cần khai báo mục đích trước khi xem hồ sơ của bệnh nhân này."}</L10n></p><RecordAccessPurposeForm patientId={activePatientId} role={role} onDeclared={() => void load(activePatientId, 0, appliedFilters)} /></section> : null}</L10nValue>
      <L10nValue>{!loading && searched && !error && !accessDenied && result?.content.length === 0 ? <AsyncState kind="empty" message={localize("Chưa có lượt khám")} /> : null}</L10nValue>

      <L10nValue>{!loading && !error && !accessDenied && result && result.content.length > 0 ? (
        <div className="overflow-hidden rounded-xl border border-border bg-surface">
          <div className="overflow-x-auto">
            <table className="w-full min-w-4xl text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3"><L10n>{"Ngày khám"}</L10n></th>
                  <th scope="col" className="px-4 py-3">Khoa</th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Bác sĩ"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Loại lượt"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Trạng thái"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Hồ sơ"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Lượt trước"}</L10n></th>
                </tr>
              </thead>
              <L10nValue>{groups.map((group) => (
                <tbody key={group.key}>
                  <L10nValue>{group.heading ? <tr className="border-b border-border bg-surface-muted"><th colSpan={7} scope="rowgroup" className="px-4 py-3 font-semibold">{group.heading}</th></tr> : null}</L10nValue>
                  <L10nValue>{group.entries.map((entry) => (
                    <tr key={`${group.key}-${entry.recordId}`} className="border-b border-border align-top last:border-0">
                      <td className="whitespace-nowrap px-4 py-3"><L10nValue>{formatExaminationDate(entry.examinationDate)}</L10nValue></td>
                      <td className="px-4 py-3"><ReferenceText kind="department" value={entry.departmentId} fallback="Khoa" /></td>
                      <td className="px-4 py-3"><ReferenceText kind="doctor" value={entry.responsibleDoctorId ?? entry.doctorId} fallback="Bác sĩ" /></td>
                      <td className="px-4 py-3"><L10nValue>{entry.visitKind ? visitKindLabels[entry.visitKind] : "Không rõ"}</L10nValue></td>
                      <td className="px-4 py-3"><L10nValue>{statusLabels[entry.status]}</L10nValue></td>
                      <td className="px-4 py-3"><Link href={`/records/${entry.recordId}`} className="text-primary underline-offset-4 hover:underline"><L10n>{"Mở hồ sơ"}</L10n></Link></td>
                      <td className="px-4 py-3"><L10nValue>{entry.previousRecordId ? <Link href={`/records/${entry.previousRecordId}`} className="text-primary underline-offset-4 hover:underline"><L10n>{"Lượt trước"}</L10n></Link> : "—"}</L10nValue></td>
                    </tr>
                  ))}</L10nValue>
                </tbody>
              ))}</L10nValue>
            </table>
          </div>
          <L10nValue>{result.totalPages > 1 ? (
            <nav aria-label={localize("Phân trang dòng thời gian")} className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3 text-sm">
              <p aria-live="polite"><L10n>{"Trang"}</L10n> <L10nValue>{result.number + 1}</L10nValue> / {result.totalPages}</p>
              <div className="flex gap-2">
                <button type="button" disabled={loading || result.number <= 0} onClick={() => { const next = Math.max(0, result.number - 1); setPageNumber(next); void load(activePatientId, next, appliedFilters); }} className="min-h-10 rounded-lg border border-border px-3 py-2 disabled:opacity-50"><L10n>{"Trang trước"}</L10n></button>
                <button type="button" disabled={loading || result.number + 1 >= result.totalPages} onClick={() => { const next = result.number + 1; setPageNumber(next); void load(activePatientId, next, appliedFilters); }} className="min-h-10 rounded-lg border border-border px-3 py-2 disabled:opacity-50"><L10n>{"Trang sau"}</L10n></button>
              </div>
            </nav>
          ) : null}</L10nValue>
        </div>
      ) : null}</L10nValue>
    </section>
  );
}
