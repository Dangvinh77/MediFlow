"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, useSyncExternalStore, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { DataTableShell } from "@/components/ui/DataTableShell";
import { controlClassName, Field } from "@/components/ui/Field";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { formatInstant } from "@/lib/format";
import type { Role } from "@/lib/roles";
import { isUuid } from "@/lib/validation";
import { inpatientApi, type AdmissionSearchParams } from "../api";
import { admissionPriorityLabel, admissionStatusPresentation } from "../presentation";
import type { AdmissionDTO, AdmissionStatus } from "../types";

const PAGE_SIZE = 20;
const detailRoles: readonly Role[] = ["ADMIN", "DOCTOR", "NURSE", "CASHIER"];
const getServerRole = (): Role | null => null;
const statuses = Object.keys(admissionStatusPresentation) as AdmissionStatus[];

interface RequestError { message: string; correlationId: string | null }
interface AdmissionRequest { page: number; filters: AdmissionSearchParams }

function requestError(cause: unknown): RequestError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : "Không thể tải danh sách nội trú.", correlationId: null };
}

export function AdmissionTable() {
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const [items, setItems] = useState<AdmissionDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [departmentId, setDepartmentId] = useState("");
  const [patientId, setPatientId] = useState("");
  const [status, setStatus] = useState<AdmissionStatus | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [activeFilters, setActiveFilters] = useState<AdmissionSearchParams>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState<AdmissionRequest>({ page: 0, filters: {} });

  const load = useCallback(async (page: number, filters: AdmissionSearchParams) => {
    setLastRequest({ page, filters });
    setLoading(true);
    setError(null);
    try {
      const result = await inpatientApi.searchAdmissions({ ...filters, page, size: PAGE_SIZE });
      setItems(result.content);
      setPageNumber(result.number);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      setError(requestError(cause));
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(0, {}), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedDepartmentId = departmentId.trim();
    const normalizedPatientId = patientId.trim();
    if (normalizedDepartmentId && !isUuid(normalizedDepartmentId)) {
      setValidationError("Mã khoa phải là UUID hợp lệ.");
      return;
    }
    if (normalizedPatientId && !isUuid(normalizedPatientId)) {
      setValidationError("Mã bệnh nhân phải là UUID hợp lệ.");
      return;
    }
    if (from && to && from > to) {
      setValidationError("Ngày bắt đầu không được sau ngày kết thúc.");
      return;
    }
    const filters: AdmissionSearchParams = {
      departmentId: normalizedDepartmentId || undefined,
      patientId: normalizedPatientId || undefined,
      status: status || undefined,
      from: from || undefined,
      to: to || undefined,
    };
    setValidationError(null);
    setActiveFilters(filters);
    void load(0, filters);
  }

  function reset() {
    setDepartmentId("");
    setPatientId("");
    setStatus("");
    setFrom("");
    setTo("");
    setValidationError(null);
    setActiveFilters({});
    void load(0, {});
  }

  return (
    <section className="mt-6">
      <div className="mb-5 flex justify-end">
        <Link href="/inpatient/beds" className="inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-muted">
          Danh mục giường
        </Link>
      </div>
      <form onSubmit={submit} className="grid gap-4 rounded-xl border border-border bg-surface p-4 md:grid-cols-2 xl:grid-cols-5">
        <Field label="Mã khoa" htmlFor="admission-department">
          <input id="admission-department" value={departmentId} onChange={(event) => setDepartmentId(event.target.value)} placeholder="UUID khoa" className={controlClassName} />
        </Field>
        <Field label="Mã bệnh nhân" htmlFor="admission-patient">
          <input id="admission-patient" value={patientId} onChange={(event) => setPatientId(event.target.value)} placeholder="UUID bệnh nhân" className={controlClassName} />
        </Field>
        <Field label="Trạng thái" htmlFor="admission-status">
          <select id="admission-status" value={status} onChange={(event) => setStatus(event.target.value as AdmissionStatus | "")} className={controlClassName}>
            <option value="">Mọi trạng thái</option>
            {statuses.map((value) => <option key={value} value={value}>{admissionStatusPresentation[value].label}</option>)}
          </select>
        </Field>
        <Field label="Từ ngày" htmlFor="admission-from">
          <input id="admission-from" type="date" value={from} onChange={(event) => setFrom(event.target.value)} className={controlClassName} />
        </Field>
        <Field label="Đến ngày" htmlFor="admission-to">
          <input id="admission-to" type="date" value={to} onChange={(event) => setTo(event.target.value)} className={controlClassName} />
        </Field>
        <div className="flex flex-wrap gap-2 md:col-span-2 xl:col-span-5">
          <Button type="submit" variant="primary" disabled={loading}>Lọc danh sách</Button>
          <Button type="button" onClick={reset} disabled={loading}>Xóa bộ lọc</Button>
        </div>
      </form>

      {validationError ? <p role="alert" className="mt-2 text-sm text-danger">{validationError}</p> : null}
      {loading && items.length === 0 ? <AsyncState kind="loading" message="Đang tải đợt nội trú…" /> : null}
      {!loading && error ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => void load(lastRequest.page, lastRequest.filters)} /> : null}
      {!loading && !error && items.length === 0 ? <AsyncState kind="empty" message="Chưa có đợt nội trú phù hợp." /> : null}

      {!error && items.length > 0 ? (
        <>
          <DataTableShell title="Danh sách đợt nội trú" description={`${totalElements} hồ sơ theo bộ lọc hiện tại`}>
            <table className="w-full min-w-5xl text-left text-sm">
              <thead className="bg-surface-muted"><tr><th className="px-4 py-3">Yêu cầu</th><th className="px-4 py-3">Bệnh nhân</th><th className="px-4 py-3">Khoa</th><th className="px-4 py-3">Ưu tiên</th><th className="px-4 py-3">Trạng thái</th><th className="px-4 py-3">Giường</th></tr></thead>
              <tbody>{items.map((item) => {
                const lifecycle = admissionStatusPresentation[item.status];
                const canOpen = role !== null && detailRoles.includes(role);
                return (
                  <tr key={item.maDotNoiTru} className="border-t border-border align-top">
                    <td className="px-4 py-3"><span className="block text-xs text-muted-foreground">{formatInstant(item.thoiGianYeuCau)}</span>{canOpen ? <Link href={`/inpatient/${item.maDotNoiTru}`} className="mt-1 block break-all font-mono text-xs text-primary hover:underline">{item.maDotNoiTru}</Link> : <span className="mt-1 block break-all font-mono text-xs">{item.maDotNoiTru}</span>}</td>
                    <td className="break-all px-4 py-3 font-mono text-xs">{item.maBenhNhan}</td>
                    <td className="break-all px-4 py-3 font-mono text-xs">{item.maKhoa}</td>
                    <td className="px-4 py-3">{admissionPriorityLabel[item.doUuTien]}{item.capCuu ? <span className="block text-xs font-semibold text-danger">Cấp cứu</span> : null}</td>
                    <td className="px-4 py-3"><StatusBadge tone={lifecycle.tone}>{lifecycle.label}</StatusBadge></td>
                    <td className="break-all px-4 py-3 font-mono text-xs">{item.maGiuongDangSuDung ?? "—"}</td>
                  </tr>
                );
              })}</tbody>
            </table>
          </DataTableShell>
          <Pagination page={pageNumber} totalPages={totalPages} totalElements={totalElements} loading={loading} onPageChange={(page) => void load(page, activeFilters)} />
        </>
      ) : null}
    </section>
  );
}
