"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { DataTableShell } from "@/components/ui/DataTableShell";
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
      {notice === "deleted" ? <p role="status" className="mb-4 rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">Đã xóa bệnh nhân.</p> : null}
      <DataTableShell title="Hồ sơ bệnh nhân" description="Tra cứu theo họ tên hoặc CMND/CCCD. Chọn Detail để xem hồ sơ." actions={(role === "ADMIN" || role === "NURSE") ? <Link href="/patients/new" className="inline-flex min-h-11 items-center justify-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Thêm bệnh nhân</Link> : undefined}>
      <form onSubmit={onSearch} className="flex flex-col gap-3 border-t border-border bg-surface-muted/40 p-4 sm:flex-row sm:items-end sm:px-5">
        <div className="min-w-0 flex-1 sm:max-w-lg"><Field label="Từ khóa bệnh nhân" htmlFor="patient-keyword"><input id="patient-keyword" type="search" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="Nhập họ tên hoặc CMND/CCCD" className={controlClassName} /></Field></div>
        <Button type="submit" variant="primary" disabled={loading}>Tìm kiếm</Button>
        <Button type="button" onClick={onReset} disabled={loading}>Xóa lọc</Button>
      </form>

      {loading ? <AsyncState kind="loading" message="Đang tải bệnh nhân…" /> : null}

      {!loading && error ? (
        <AsyncState
          kind="error"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={() => void loadPatients(lastRequest.current)}
        />
      ) : null}

      {!loading && !error && patients.length === 0 ? (
        <AsyncState kind="empty" message="Chưa có bệnh nhân phù hợp." />
      ) : null}

      {!loading && !error && patients.length > 0 ? (
        <>
          <div className="overflow-x-auto border-t border-border">
            <table className="w-full min-w-3xl text-left text-sm"><caption className="sr-only">Danh sách hồ sơ bệnh nhân</caption>
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">Họ tên</th>
                  <th scope="col" className="px-4 py-3">Ngày sinh</th>
                  <th scope="col" className="px-4 py-3">Giới tính</th>
                  <th scope="col" className="px-4 py-3">CMND/CCCD</th>
                  <th scope="col" className="px-4 py-3">Điện thoại</th>
                  <th scope="col" className="px-4 py-3">Action</th>
                </tr>
              </thead>
              <tbody>
                {patients.map((patient) => (
                  <tr
                    key={patient.maBenhNhan}
                    className="border-b border-border transition-colors hover:bg-surface-muted/60 focus-within:bg-surface-muted/60 last:border-0"
                  >
                    <td className="px-4 py-3 font-medium"><span className="flex items-center gap-3"><span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary">{patient.hoTen.trim().split(/\s+/).slice(-2).map((part) => part[0]).join("")}</span><span>{patient.hoTen}</span></span></td>
                    <td className="px-4 py-3">{formatLocalDate(patient.ngaySinh)}</td>
                    <td className="px-4 py-3">{patient.gioiTinh === "M" ? "Nam" : "Nữ"}</td>
                    <td className="px-4 py-3">{patient.soCmnd}</td>
                    <td className="px-4 py-3">{patient.soDienThoai ?? "—"}</td>
                    <td className="px-4 py-3"><Link href={`/patients/${patient.maBenhNhan}`} aria-label={`Detail ${patient.hoTen}`} className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Detail</Link></td>
                  </tr>
                ))}
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
      ) : null}
      </DataTableShell>
    </section>
  );
}
