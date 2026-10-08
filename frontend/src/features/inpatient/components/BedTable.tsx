"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { DataTableShell } from "@/components/ui/DataTableShell";
import { controlClassName, Field } from "@/components/ui/Field";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { isUuid } from "@/lib/validation";
import { inpatientApi, type BedSearchParams } from "../api";
import { bedStatusPresentation } from "../presentation";
import type { BedDTO, BedStatus } from "../types";

const PAGE_SIZE = 20;
const statuses = Object.keys(bedStatusPresentation) as BedStatus[];
interface RequestError { message: string; correlationId: string | null }
interface BedRequest { page: number; filters: BedSearchParams }

export function BedTable() {
  const router = useRouter();
  const [items, setItems] = useState<BedDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [departmentId, setDepartmentId] = useState("");
  const [wardCode, setWardCode] = useState("");
  const [status, setStatus] = useState<BedStatus | "">("");
  const [activeFilters, setActiveFilters] = useState<BedSearchParams>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState<BedRequest>({ page: 0, filters: {} });

  const load = useCallback(async (page: number, filters: BedSearchParams) => {
    setLastRequest({ page, filters });
    setLoading(true);
    setError(null);
    try {
      const result = await inpatientApi.searchBeds({ ...filters, page, size: PAGE_SIZE });
      setItems(result.content);
      setPageNumber(result.number);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      setError({ message: cause instanceof Error ? cause.message : "Không thể tải danh mục giường.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
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
    const department = departmentId.trim();
    if (department && !isUuid(department)) {
      setValidationError("Mã khoa phải là UUID hợp lệ.");
      return;
    }
    const filters: BedSearchParams = { departmentId: department || undefined, wardCode: wardCode.trim() || undefined, status: status || undefined };
    setValidationError(null);
    setActiveFilters(filters);
    void load(0, filters);
  }

  function reset() {
    setDepartmentId(""); setWardCode(""); setStatus(""); setValidationError(null); setActiveFilters({}); void load(0, {});
  }

  return (
    <section className="mt-6">
      <div className="mb-5"><Link href="/inpatient" className="inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-muted">Quay lại đợt nội trú</Link></div>
      <form onSubmit={submit} className="grid gap-4 rounded-xl border border-border bg-surface p-4 md:grid-cols-3">
        <Field label="Mã khoa" htmlFor="bed-department"><input id="bed-department" value={departmentId} onChange={(event) => setDepartmentId(event.target.value)} placeholder="UUID khoa" className={controlClassName} /></Field>
        <Field label="Mã khu" htmlFor="bed-ward"><input id="bed-ward" value={wardCode} onChange={(event) => setWardCode(event.target.value)} placeholder="Ví dụ: ICU-A" className={controlClassName} /></Field>
        <Field label="Trạng thái" htmlFor="bed-status"><select id="bed-status" value={status} onChange={(event) => setStatus(event.target.value as BedStatus | "")} className={controlClassName}><option value="">Mọi trạng thái</option>{statuses.map((value) => <option key={value} value={value}>{bedStatusPresentation[value].label}</option>)}</select></Field>
        <div className="flex flex-wrap gap-2 md:col-span-3"><Button type="submit" variant="primary" disabled={loading}>Lọc giường</Button><Button type="button" onClick={reset} disabled={loading}>Xóa bộ lọc</Button></div>
      </form>
      {validationError ? <p role="alert" className="mt-2 text-sm text-danger">{validationError}</p> : null}
      {loading && items.length === 0 ? <AsyncState kind="loading" message="Đang tải danh mục giường…" /> : null}
      {!loading && error ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => void load(lastRequest.page, lastRequest.filters)} /> : null}
      {!loading && !error && items.length === 0 ? <AsyncState kind="empty" message="Không có giường phù hợp." /> : null}
      {!error && items.length > 0 ? <><DataTableShell title="Danh mục giường" description={`${totalElements} giường theo bộ lọc hiện tại`}><table className="w-full min-w-4xl text-left text-sm"><thead className="bg-surface-muted"><tr><th className="px-4 py-3">Giường</th><th className="px-4 py-3">Khoa</th><th className="px-4 py-3">Khu / phòng</th><th className="px-4 py-3">Loại</th><th className="px-4 py-3">Trạng thái</th><th className="px-4 py-3">Kích hoạt</th></tr></thead><tbody>{items.map((bed) => { const presentation = bedStatusPresentation[bed.status]; return <tr key={bed.maGiuong} className="border-t border-border"><td className="px-4 py-3"><span className="block font-semibold">{bed.maGiuongTrongPhong}</span><span className="break-all font-mono text-xs text-muted-foreground">{bed.maGiuong}</span></td><td className="break-all px-4 py-3 font-mono text-xs">{bed.maKhoa}</td><td className="px-4 py-3">{bed.maKhu} / {bed.maPhong}</td><td className="px-4 py-3">{bed.loaiGiuong}</td><td className="px-4 py-3"><StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge></td><td className="px-4 py-3"><StatusBadge tone={bed.active ? "success" : "neutral"}>{bed.active ? "Đang dùng" : "Đã tắt"}</StatusBadge></td></tr>; })}</tbody></table></DataTableShell><Pagination page={pageNumber} totalPages={totalPages} totalElements={totalElements} loading={loading} onPageChange={(page) => void load(page, activeFilters)} /></> : null}
    </section>
  );
}
