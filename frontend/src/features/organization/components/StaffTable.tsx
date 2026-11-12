"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { DirectorySection as DataTableShell } from "./RecordLayout";
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
  const { localize } = useLocale();
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
    <DataTableShell title={localize("Nhân sự")} description={localize("Hồ sơ nhân sự, chức danh và khoa công tác.")}>
      <form className="grid items-end gap-3 border-t border-border p-5 lg:grid-cols-[minmax(16rem,2fr)_auto_minmax(11rem,1fr)_minmax(11rem,1fr)] lg:px-6" onSubmit={event => { event.preventDefault(); setLoading(true); setError(null); setLastRequest(0); setRequest(previous => ({ ...previous, page: 0, keyword: keyword.trim() || undefined, attempt: previous.attempt + 1 })); }}>
        <Field label={localize("Tìm nhân sự")} htmlFor="staff-keyword"><input id="staff-keyword" type="search" maxLength={100} value={keyword} onChange={event => setKeyword(event.target.value)} placeholder={localize(getRole() === "ADMIN" ? "Họ tên, CCCD, chức danh hoặc khoa/phòng" : "Họ tên, chức danh hoặc khoa/phòng")} className={controlClassName} /></Field>
        <button type="submit" className="min-h-11 rounded-lg border border-control-border px-4 py-2 text-sm font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"><L10n>{"Tìm kiếm"}</L10n></button>

        <Field label={localize("Lọc theo khoa")} htmlFor="staff-department-filter"><select id="staff-department-filter" className={controlClassName} value={request.departmentId ?? ""} onChange={event => { setLoading(true); setError(null); setLastRequest(0); setRequest(previous => ({ ...previous, page: 0, departmentId: event.target.value || undefined })); }}><option value=""><L10n>{"Tất cả khoa"}</L10n></option><L10nValue>{departments.map(department => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}<L10nValue>{department.active ? "" : " (ngừng hoạt động)"}</L10nValue></option>)}</L10nValue></select></Field>
        <Field label={localize("Lọc theo chức danh")} htmlFor="staff-job-filter"><select id="staff-job-filter" className={controlClassName} value={request.jobTitle ?? ""} onChange={event => { setLoading(true); setError(null); setLastRequest(0); setRequest(previous => ({ ...previous, page: 0, jobTitle: (event.target.value || undefined) as JobTitle | undefined })); }}><option value=""><L10n>{"Tất cả chức danh"}</L10n></option><L10nValue>{Object.entries(jobTitleLabels).map(([value, label]) => <option key={value} value={value}><L10nValue>{label}</L10nValue></option>)}</L10nValue></select></Field>
      </form>
      <L10nValue>{loading ? <AsyncState kind="loading" message={localize("Đang tải danh sách nhân sự…")} /> : null}</L10nValue>

      <L10nValue>{!loading && error ? (
        <AsyncState
          kind="error"
          message={localize(error.message)}
          correlationId={error.correlationId}
          onRetry={() => void loadStaff(lastRequest)}
        />
      ) : null}</L10nValue>

      <L10nValue>{!loading && !error && staff.length === 0 ? (
        <AsyncState kind="empty" message={localize("Chưa có nhân sự nào.")} />
      ) : null}</L10nValue>

      <L10nValue>{!loading && !error && staff.length > 0 ? (
        <>
          <p role="status" className="border-t border-border px-5 py-3 text-sm text-muted-foreground lg:px-6"><span className="font-semibold tabular-nums text-foreground"><L10nValue>{totalElements}</L10nValue></span><L10n>{" nhân sự"}</L10n><L10nValue>{request.keyword || request.departmentId || request.jobTitle ? " phù hợp với bộ lọc" : " trong danh sách"}</L10nValue></p>
          <div className="overflow-x-auto border-t border-border">
            <table className="w-full min-w-3xl text-left text-sm">
            <caption className="sr-only"><L10n>{"Nhân sự"}</L10n></caption>
              <thead className="border-b border-border text-muted-foreground">
                <tr>
                  <th scope="col" className="px-4 py-3"><L10n>{"Họ tên"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Chức danh"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Chuyên môn"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Khoa công tác"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Trạng thái"}</L10n></th>
                  <th scope="col" className="px-4 py-3"><L10n>{"Thao tác"}</L10n></th>
                </tr>
              </thead>
              <tbody>
                <L10nValue>{staff.map((member) => (
                  <tr key={member.staffId} className="border-b border-border transition-colors hover:bg-surface-muted/60 focus-within:bg-surface-muted/60 last:border-0">
                    <td className="px-4 py-2 font-medium"><span className="flex items-center gap-3"><span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-muted text-xs font-semibold text-foreground"><L10nValue>{member.fullName.trim().split(/\s+/).slice(-2).map((part) => part[0]).join("")}</L10nValue></span><Link href={`/organization/staff/${member.staffId}`} className="rounded font-semibold underline-offset-4 hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">{member.fullName}</Link></span></td>
                    <td className="px-4 py-2">
                      <L10nValue>{jobTitleLabels[member.jobTitle] ?? "Không xác định"}</L10nValue>
                    </td>
                    <td className="px-4 py-2"><L10nValue>{member.specialization ?? "—"}</L10nValue></td>
                    <td className="px-4 py-2"><L10nValue>{departments.find(department => department.departmentId === member.departmentId)?.departmentName ?? "Chưa có thông tin khoa/phòng"}</L10nValue></td>
                    <td className="px-4 py-2">
                      <StatusBadge tone={member.active ? "success" : "neutral"}>
                        <L10nValue>{member.active ? "Đang làm việc" : "Đã ngừng làm việc"}</L10nValue>
                      </StatusBadge>
                    </td>
                    <td className="px-4 py-2"><Link href={`/organization/staff/${member.staffId}`} aria-label={localize(`Xem chi tiết ${member.fullName}`)} className="inline-flex min-h-11 items-center rounded-lg px-2 py-2 font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"><L10n>{"Xem chi tiết"}</L10n></Link></td>
                  </tr>
                ))}</L10nValue>
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
      ) : null}</L10nValue>
    </DataTableShell>
  );
}
