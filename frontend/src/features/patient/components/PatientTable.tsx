"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { DirectorySection as DataTableShell } from "./RecordLayout";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { getSessionKey } from "@/lib/session";
import { formatLocalDate } from "@/lib/format";
import { patientApi } from "../api";
import type { PatientDTO } from "../types";

const PATIENT_PAGE_SIZE = 20;
const getServerRole = () => null;

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

export function PatientTable(props: { notice?: "deleted" }) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, getServerRole);
  return <PatientTableWorkspace key={session} {...props} />;
}

function PatientTableWorkspace({ notice }: { notice?: "deleted" }) {
  const { localize } = useLocale();
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const [patients, setPatients] = useState<PatientDTO[]>([]);
  const [error, setError] = useState<RequestError | null>(null);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [activeKeyword, setActiveKeyword] = useState("");
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const lastRequest = useRef<PatientRequest>({
    keyword: "",
    page: 0,
  });
  const requestSequence = useRef(0);

  const loadPatients = useCallback(async (request: PatientRequest) => {
    lastRequest.current = request;
    const requestId = ++requestSequence.current;
    const token = getSessionKey();
    setLoading(true);
    setError(null);

    try {
      const result = await patientApi.search({
        keyword: request.keyword || undefined,
        page: request.page,
        size: PATIENT_PAGE_SIZE,
      });
      if (requestId !== requestSequence.current || token !== getSessionKey()) return;

      setPatients(result.content);
      setPageNumber(result.number);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (cause: unknown) {
      if (requestId !== requestSequence.current || token !== getSessionKey()) return;

      if (cause instanceof ApiRequestError && cause.status === 401) {
        setPatients([]);
        router.replace("/login");
        return;
      }

      setError(getRequestError(cause));
    } finally {
      if (requestId === requestSequence.current && token === getSessionKey()) setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    const initialLoad = window.setTimeout(() => {
      void loadPatients({ keyword: "", page: 0 });
    }, 0);

    return () => { window.clearTimeout(initialLoad); requestSequence.current += 1; };
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
    <section>
      <L10nValue>{notice === "deleted" ? <p role="status" className="mb-4 rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success"><L10n>{"Đã lưu trữ bệnh nhân. Mã và dữ liệu hồ sơ vẫn được giữ lại."}</L10n></p> : null}</L10nValue>
      <DataTableShell title={localize("Hồ sơ bệnh nhân")} description={localize("Tra cứu theo họ tên hoặc CMND/CCCD. Mở hồ sơ để xem thông tin và cập nhật.")} actions={(role === "ADMIN" || role === "NURSE") ? <Link href="/patients/new" className="inline-flex min-h-11 items-center justify-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"><L10n>{"Thêm bệnh nhân"}</L10n></Link> : undefined}>
      <form onSubmit={onSearch} className="flex flex-col gap-3 border-t border-border p-5 sm:flex-row sm:items-end lg:px-6">
        <div className="min-w-0 flex-1 sm:max-w-lg"><Field label={localize("Từ khóa bệnh nhân")} htmlFor="patient-keyword"><input id="patient-keyword" type="search" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder={localize("Nhập họ tên hoặc CMND/CCCD")} className={controlClassName} /></Field></div>
        <Button type="submit" disabled={loading}><L10n>{"Tìm kiếm"}</L10n></Button>
        <Button type="button" onClick={onReset} disabled={loading}><L10n>{"Xóa lọc"}</L10n></Button>
      </form>

      <L10nValue>{loading ? <AsyncState kind="loading" message={localize("Đang tải bệnh nhân…")} /> : null}</L10nValue>

      <L10nValue>{!loading && error ? (
        <AsyncState
          kind="error"
          message={localize(error.message)}
          correlationId={error.correlationId}
          onRetry={() => void loadPatients(lastRequest.current)}
        />
      ) : null}</L10nValue>

      <L10nValue>{!loading && !error && patients.length === 0 ? (
        <AsyncState kind="empty" message={localize("Chưa có bệnh nhân phù hợp.")} />
      ) : null}</L10nValue>

      <L10nValue>{!loading && !error && patients.length > 0 ? (
        <>
          <p role="status" className="border-t border-border px-5 py-3 text-sm text-muted-foreground lg:px-6"><span className="font-semibold tabular-nums text-foreground"><L10nValue>{totalElements}</L10nValue></span><L10n>{" hồ sơ"}</L10n><L10nValue>{activeKeyword ? <><L10n>{" phù hợp với “"}</L10n><L10nValue>{activeKeyword}</L10nValue>”</> : null}</L10nValue></p>
          <div className="overflow-x-auto border-t border-border">
            <table className="w-full min-w-3xl text-left text-sm"><caption className="sr-only"><L10n>{"Danh sách hồ sơ bệnh nhân"}</L10n></caption>
              <thead className="border-b border-border text-muted-foreground">
                <tr>
                  <th scope="col" className="px-4 py-3"><L10n>{"Họ tên"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Ngày sinh"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Giới tính"}</L10n></th>
                  <th scope="col" className="px-4 py-3">CMND/CCCD</th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Điện thoại"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Thao tác"}</L10n></th>
                </tr>
              </thead>
              <tbody>
                <L10nValue>{patients.map((patient) => (
                  <tr
                    key={patient.maBenhNhan}
                    className="border-b border-border transition-colors hover:bg-surface-muted/60 focus-within:bg-surface-muted/60 last:border-0"
                  >
                    <td className="px-4 py-2 font-medium"><span className="flex items-center gap-3"><span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-muted text-xs font-semibold text-foreground"><L10nValue>{patient.hoTen.trim().split(/\s+/).slice(-2).map((part) => part[0]).join("")}</L10nValue></span><Link href={`/patients/${patient.maBenhNhan}`} className="rounded font-semibold underline-offset-4 hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">{patient.hoTen}</Link></span></td>
                    <td className="whitespace-nowrap px-4 py-2 tabular-nums"><L10nValue>{formatLocalDate(patient.ngaySinh)}</L10nValue></td>
                    <td className="px-4 py-2"><L10nValue>{patient.gioiTinh === "M" ? "Nam" : "Nữ"}</L10nValue></td>
                    <td className="whitespace-nowrap px-4 py-2 tabular-nums">{patient.soCmnd}</td>
                    <td className="px-4 py-2"><L10nValue>{patient.soDienThoai ?? "—"}</L10nValue></td>
                    <td className="px-4 py-2"><Link href={`/patients/${patient.maBenhNhan}`} aria-label={localize(`Xem chi tiết ${patient.hoTen}`)} className="inline-flex min-h-11 items-center rounded-lg px-2 py-2 font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"><L10n>{"Xem chi tiết"}</L10n></Link></td>
                  </tr>
                ))}</L10nValue>
              </tbody>
            </table>
          </div>
          <div className="border-t border-border px-4 pb-4"><Pagination
            loading={loading}
            page={pageNumber}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={(page) => void loadPatients({ keyword: activeKeyword, page })}
          /></div>
        </>
      ) : null}</L10nValue>
      </DataTableShell>
    </section>
  );
}
