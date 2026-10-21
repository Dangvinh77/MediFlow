"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { organizationApi } from "../api";
import { getToken } from "@/lib/auth";
import { isRequestSessionCurrent } from "../request-session";
import type { JobTitle, StaffDTO } from "../types";

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

export function StaffTable() {
  const router = useRouter();
  const [staff, setStaff] = useState<StaffDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RequestError | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState(0);

  const [request, setRequest] = useState({ page: 0, attempt: 0 });

  function loadStaff(page: number) {
    setLastRequest(page);
    setLoading(true);
    setError(null);
    setRequest((previous) => ({ page, attempt: previous.attempt + 1 }));
  }

  useEffect(() => {
    let disposed = false;
    const sessionToken = getToken();
    organizationApi.staff({ page: request.page, size: STAFF_PAGE_SIZE })
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
    <section className="mt-10" aria-labelledby="organization-staff-heading">
      <div className="mb-4">
        <h2 id="organization-staff-heading" className="text-lg font-semibold">
          Nhân sự
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Danh sách nhân sự theo từng trang.
        </p>
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
          <div className="overflow-x-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-4xl text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">Họ tên</th>
                  <th scope="col" className="px-4 py-3">Chức danh</th>
                  <th scope="col" className="px-4 py-3">Chuyên môn</th>
                  <th scope="col" className="px-4 py-3">Mã khoa</th>
                  <th scope="col" className="px-4 py-3">Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {staff.map((member) => (
                  <tr key={member.staffId} className="border-b border-border last:border-0">
                    <td className="px-4 py-3 font-medium"><Link href={`/organization/staff/${member.staffId}`} className="text-primary hover:underline">{member.fullName}</Link></td>
                    <td className="px-4 py-3">
                      {jobTitleLabels[member.jobTitle] ?? "Không xác định"}
                    </td>
                    <td className="px-4 py-3">{member.specialization ?? "—"}</td>
                    <td className="break-all px-4 py-3 font-mono text-xs">{member.departmentId}</td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={member.active ? "success" : "neutral"}>
                        {member.active ? "Đang làm việc" : "Đã ngừng làm việc"}
                      </StatusBadge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={pageNumber}
            totalPages={totalPages}
            totalElements={totalElements}
            loading={loading}
            onPageChange={(page) => void loadStaff(page)}
          />
        </>
      ) : null}
    </section>
  );
}
