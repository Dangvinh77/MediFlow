"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { DataTableShell } from "@/components/ui/DataTableShell";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { organizationApi } from "../api";
import { getRole, getSessionKey } from "@/lib/auth";
import { isRequestSessionCurrent } from "../request-session";
import type { DepartmentDTO, JobTitle, StaffDTO } from "../types";

const STAFF_PAGE_SIZE = 20;

const jobTitleLabels: Record<JobTitle, string> = {
  DOCTOR: "Bác sĩ",
  NURSE: "Điều dưỡng",
  TECHNICIAN: "Kỹ thuật viên",
  PHARMACIST: "Dược sĩ",
  CASHIER: "Thu ngân",
  MANAGER: "Quản lý",
  ADMINISTRATIVE: "Hành chính",
};

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
    message: cause instanceof Error ? cause.message : "Không thể tải danh sách nhân sự.",
    correlationId: null,
  };
}

export function StaffTable({ departments = [] }: { departments?: DepartmentDTO[] }) {
  const router = useRouter();
  const [staff, setStaff] = useState<StaffDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RequestError | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState(0);
  const [keyword, setKeyword] = useState("");

  const [request, setRequest] = useState<{ page: number; attempt: number; departmentId?: string; jobTitle?: JobTitle; keyword?: string }>({ page: 0, attempt: 0 });

  function loadStaff(page: number) {
    setLastRequest(page);
    setLoading(true);
    setError(null);
    setRequest((previous) => ({ ...previous, page, attempt: previous.attempt + 1 }));
  }

  useEffect(() => {
    let disposed = false;
    const sessionToken = getSessionKey();
    organizationApi.staff({ page: request.page, size: STAFF_PAGE_SIZE, ...(request.keyword ? { keyword: request.keyword } : {}), ...(request.departmentId ? { departmentId: request.departmentId } : {}), ...(request.jobTitle ? { jobTitle: request.jobTitle } : {}) })
      .then((result) => {
        if (disposed || !isRequestSessionCurrent(sessionToken)) return;
        setStaff(result.content);
        setPageNumber(result.number);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      })
      .catch((cause: unknown) => {
        if (disposed || !isRequestSessionCurrent(sessionToken, cause)) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }
        setError(getRequestError(cause));
      })
      .finally(() => {
        if (!disposed && isRequestSessionCurrent(sessionToken)) setLoading(false);
      });
    return () => { disposed = true; };
  }, [router, request]);

  return (
    <DataTableShell title="Nhân sự" description="Hồ sơ nhân sự, chức danh và khoa công tác.">
      <form className="flex flex-wrap items-end gap-3 border-t border-border p-4" onSubmit={event => { event.preventDefault(); setLoading(true); setError(null); setLastRequest(0); setRequest(previous => ({ ...previous, page: 0, keyword: keyword.trim() || undefined, attempt: previous.attempt + 1 })); }}>
        <Field label="Tìm nhân sự" htmlFor="staff-keyword"><input id="staff-keyword" type="search" maxLength={100} value={keyword} onChange={event => setKeyword(event.target.value)} placeholder={getRole() === "ADMIN" ? "Họ tên, CCCD, chức danh hoặc khoa/phòng" : "Họ tên, chức danh hoặc khoa/phòng"} className={controlClassName} /></Field>
        <button type="submit" className="min-h-11 rounded-lg border border-control-border px-4 py-2 text-sm font-semibold hover:bg-surface-muted">Tìm kiếm</button>
      </form>
      <div className="grid gap-3 border-t border-border p-4 sm:grid-cols-2">
        <Field label="Lọc theo khoa" htmlFor="staff-department-filter"><select id="staff-department-filter" className={controlClassName} value={request.departmentId ?? ""} onChange={event => { setLoading(true); setError(null); setLastRequest(0); setRequest(previous => ({ ...previous, page: 0, departmentId: event.target.value || undefined })); }}><option value="">Tất cả khoa</option>{departments.map(department => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}{department.active ? "" : " (ngừng hoạt động)"}</option>)}</select></Field>
        <Field label="Lọc theo chức danh" htmlFor="staff-job-filter"><select id="staff-job-filter" className={controlClassName} value={request.jobTitle ?? ""} onChange={event => { setLoading(true); setError(null); setLastRequest(0); setRequest(previous => ({ ...previous, page: 0, jobTitle: (event.target.value || undefined) as JobTitle | undefined })); }}><option value="">Tất cả chức danh</option>{Object.entries(jobTitleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
      </div>
      {loading ? <AsyncState kind="loading" message="Đang tải danh sách nhân sự…" /> : null}

      {!loading && error ? (
        <AsyncState
          kind="error"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={() => void loadStaff(lastRequest)}
        />
      ) : null}

      {!loading && !error && staff.length === 0 ? (
        <AsyncState kind="empty" message="Chưa có nhân sự nào." />
      ) : null}

      {!loading && !error && staff.length > 0 ? (
        <>
          <div className="overflow-x-auto border-t border-border">
            <table className="w-full min-w-3xl text-left text-sm">
            <caption className="sr-only">Nhân sự</caption>
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">Họ tên</th>
                  <th scope="col" className="px-4 py-3">Chức danh</th>
                  <th scope="col" className="px-4 py-3">Chuyên môn</th>
                  <th scope="col" className="px-4 py-3">Khoa công tác</th>
                  <th scope="col" className="px-4 py-3">Trạng thái</th>
                  <th scope="col" className="px-4 py-3">Action</th>
                </tr>
              </thead>
              <tbody>
                {staff.map((member) => (
                  <tr key={member.staffId} className="border-b border-border transition-colors hover:bg-surface-muted/60 focus-within:bg-surface-muted/60 last:border-0">
                    <td className="px-4 py-3 font-medium"><span className="flex items-center gap-3"><span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary">{member.fullName.trim().split(/\s+/).slice(-2).map((part) => part[0]).join("")}</span><span>{member.fullName}</span></span></td>
                    <td className="px-4 py-3">
                      {jobTitleLabels[member.jobTitle] ?? "Không xác định"}
                    </td>
                    <td className="px-4 py-3">{member.specialization ?? "—"}</td>
                    <td className="px-4 py-3">{departments.find(department => department.departmentId === member.departmentId)?.departmentName ?? "Chưa có thông tin khoa/phòng"}</td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={member.active ? "success" : "neutral"}>
                        {member.active ? "Đang làm việc" : "Đã ngừng làm việc"}
                      </StatusBadge>
                    </td>
                    <td className="px-4 py-3"><Link href={`/organization/staff/${member.staffId}`} aria-label={`Detail ${member.fullName}`} className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Detail</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="border-t border-border px-4 pb-4"><Pagination
            page={pageNumber}
            totalPages={totalPages}
            totalElements={totalElements}
            loading={loading}
            onPageChange={(page) => void loadStaff(page)}
          /></div>
        </>
      ) : null}
    </DataTableShell>
  );
}
