"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { DataTableShell } from "@/components/ui/DataTableShell";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { controlClassName, Field } from "@/components/ui/Field";
import { organizationApi } from "../api";
import { getSessionKey } from "@/lib/auth";
import { isRequestSessionCurrent } from "../request-session";
import type { DepartmentDTO, DepartmentType } from "../types";

const departmentTypeLabels: Record<DepartmentType, string> = {
  CLINICAL: "Lâm sàng",
  PARACLINICAL: "Cận lâm sàng",
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
    message: cause instanceof Error ? cause.message : "Không thể tải danh sách khoa.",
    correlationId: null,
  };
}

export function DepartmentList({ onLoaded, category = "all" }: {
  onLoaded?: (departments: DepartmentDTO[]) => void;
  category?: "all" | "departments" | "offices";
}) {
  const router = useRouter();
  const [departments, setDepartments] = useState<DepartmentDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RequestError | null>(null);
  const [keyword, setKeyword] = useState("");

  const [attempt, setAttempt] = useState(0);

  function loadDepartments() {
    setLoading(true);
    setError(null);
    setAttempt((value) => value + 1);
  }

  useEffect(() => {
    let disposed = false;
    const sessionToken = getSessionKey();
    organizationApi.departments(false)
      .then((result) => {
        if (!disposed && isRequestSessionCurrent(sessionToken)) { setDepartments(result); onLoaded?.(result); }
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
  }, [router, attempt, onLoaded]);

  const title = category === "departments" ? "Khoa" : category === "offices" ? "Phòng" : "Khoa phòng";
  const normalize = (text: string) => text.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[đĐ]/g, "d").toLowerCase();
  const visibleDepartments = departments.filter(department => (category === "all"
    || (category === "offices" ? department.departmentType === "ADMINISTRATIVE"
      : department.departmentType === "CLINICAL" || department.departmentType === "PARACLINICAL"))
    && normalize(`${department.departmentName} ${department.abbreviation}`).includes(normalize(keyword.trim())));

  return (
    <DataTableShell title={title} description={category === "departments" ? "Danh sách khoa lâm sàng, cận lâm sàng và trạng thái hoạt động." : category === "offices" ? "Danh sách phòng hành chính và trạng thái hoạt động." : "Danh sách khoa phòng và trạng thái hoạt động."}>
      <div className="border-t border-border p-4"><Field label="Tìm khoa/phòng" htmlFor="department-keyword"><input id="department-keyword" type="search" value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="Tên hoặc mã viết tắt" className={controlClassName} /></Field></div>
      {loading ? <AsyncState kind="loading" message="Đang tải danh sách khoa…" /> : null}

      {!loading && error ? (
        <AsyncState
          kind="error"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={() => void loadDepartments()}
        />
      ) : null}

      {!loading && !error && visibleDepartments.length === 0 ? (
        <AsyncState kind="empty" message={category === "offices" ? "Chưa có phòng nào." : category === "departments" ? "Chưa có khoa nào." : "Chưa có khoa phòng nào."} />
      ) : null}

      {!loading && !error && visibleDepartments.length > 0 ? (
        <div className="overflow-x-auto border-t border-border">
          <table className="w-full min-w-3xl text-left text-sm">
            <caption className="sr-only">{title}</caption>
            <thead className="border-b border-border bg-surface-muted">
              <tr>
                <th scope="col" className="px-4 py-3">{category === "offices" ? "Tên phòng" : "Tên khoa"}</th>
                <th scope="col" className="px-4 py-3">Viết tắt</th>
                <th scope="col" className="px-4 py-3">Loại</th>
                <th scope="col" className="px-4 py-3">Địa điểm</th>
                <th scope="col" className="px-4 py-3">Trạng thái</th>
                <th scope="col" className="px-4 py-3">Action</th>
              </tr>
            </thead>
            <tbody>
              {visibleDepartments.map((department) => (
                <tr key={department.departmentId} className="border-b border-border transition-colors hover:bg-surface-muted/60 focus-within:bg-surface-muted/60 last:border-0">
                  <td className="px-4 py-3 font-medium">{department.departmentName}</td>
                  <td className="px-4 py-3">{department.abbreviation}</td>
                  <td className="px-4 py-3">
                    {departmentTypeLabels[department.departmentType] ?? "Không xác định"}
                  </td>
                  <td className="px-4 py-3">{department.location || "—"}</td>
                  <td className="px-4 py-3">
                    <StatusBadge tone={department.active ? "success" : "neutral"}>
                      {department.active ? "Đang hoạt động" : "Ngừng hoạt động"}
                    </StatusBadge>
                  </td>
                  <td className="px-4 py-3"><Link href={`/organization/departments/${department.departmentId}`} aria-label={`Detail ${department.departmentName}`} className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Detail</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </DataTableShell>
  );
}
