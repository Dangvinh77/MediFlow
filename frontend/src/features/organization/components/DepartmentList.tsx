"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { DirectorySection as DataTableShell } from "./RecordLayout";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ReferenceText } from "@/components/references/ReferenceText";
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
  const { localize } = useLocale();
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

  const title = category === "departments" ? "Khoa" : category === "offices" ? "Bộ phận chức năng" : "Khoa / bộ phận";
  const normalize = (text: string) => text.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[đĐ]/g, "d").toLowerCase();
  const visibleDepartments = departments.filter(department => (category === "all"
    || (category === "offices" ? department.departmentType === "ADMINISTRATIVE"
      : department.departmentType === "CLINICAL" || department.departmentType === "PARACLINICAL"))
    && normalize(`${department.departmentName} ${department.abbreviation}`).includes(normalize(keyword.trim())));

  return (
    <DataTableShell title={localize(title)} description={localize(category === "departments" ? "Danh sách khoa lâm sàng, cận lâm sàng và trạng thái hoạt động." : category === "offices" ? "Danh sách bộ phận chức năng và trạng thái hoạt động." : "Khoa lâm sàng có trưởng khoa quản lý lịch; bộ phận cận lâm sàng và chức năng làm việc chung tại một phòng, không bổ nhiệm trưởng.")}>
      <div className="border-t border-border p-4"><Field label={localize("Tìm khoa/bộ phận")} htmlFor="department-keyword"><input id="department-keyword" type="search" value={keyword} onChange={event => setKeyword(event.target.value)} placeholder={localize("Tên hoặc mã viết tắt")} className={controlClassName} /></Field></div>
      <L10nValue>{loading ? <AsyncState kind="loading" message={localize("Đang tải danh sách khoa…")} /> : null}</L10nValue>

      <L10nValue>{!loading && error ? (
        <AsyncState
          kind="error"
          message={localize(error.message)}
          correlationId={error.correlationId}
          onRetry={() => void loadDepartments()}
        />
      ) : null}</L10nValue>

      <L10nValue>{!loading && !error && visibleDepartments.length === 0 ? (
        <AsyncState kind="empty" message={localize(category === "offices" ? "Chưa có bộ phận nào." : category === "departments" ? "Chưa có khoa nào." : "Chưa có khoa/bộ phận nào.")} />
      ) : null}</L10nValue>

      <L10nValue>{!loading && !error && visibleDepartments.length > 0 ? (
        <div className="overflow-x-auto border-t border-border">
          <table className="w-full min-w-3xl text-left text-sm">
            <caption className="sr-only"><L10nValue>{title}</L10nValue></caption>
            <thead className="border-b border-border text-muted-foreground">
              <tr>
                <th scope="col" className="px-4 py-3"><L10nValue>{category === "offices" ? "Tên bộ phận" : category === "all" ? "Tên khoa / bộ phận" : "Tên khoa"}</L10nValue></th>
                <th scope="col" className="px-4 py-3"><L10n>{"Viết tắt"}</L10n></th>
                <th scope="col" className="px-4 py-3"><L10n>{"Loại"}</L10n></th>
                <th scope="col" className="px-4 py-3"><L10n>{"Trưởng khoa"}</L10n></th>
                <th scope="col" className="px-4 py-3"><L10n>{"Địa điểm"}</L10n></th>
                <th scope="col" className="px-4 py-3"><L10n>{"Trạng thái"}</L10n></th>
                <th scope="col" className="px-4 py-3"><L10n>{"Thao tác"}</L10n></th>
              </tr>
            </thead>
            <tbody>
              <L10nValue>{visibleDepartments.map((department) => (
                <tr key={department.departmentId} className="border-b border-border transition-colors hover:bg-surface-muted/60 focus-within:bg-surface-muted/60 last:border-0">
                  <td className="px-4 py-2 font-medium"><Link href={`/organization/departments/${department.departmentId}`} className="rounded font-semibold underline-offset-4 hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">{department.departmentName}</Link></td>
                  <td className="px-4 py-2">{department.abbreviation}</td>
                  <td className="px-4 py-2">
                    <L10nValue>{departmentTypeLabels[department.departmentType] ?? "Không xác định"}</L10nValue>
                  </td>
                  <td className="px-4 py-2"><L10nValue>{department.departmentType !== "CLINICAL"
                    ? <span className="text-muted-foreground"><L10n>{"Không áp dụng"}</L10n></span>
                    : department.departmentHeadId
                      ? <ReferenceText kind="doctor" value={department.departmentHeadId} fallback="Trưởng khoa" />
                      : <StatusBadge tone="warning"><L10n>{"Chưa bổ nhiệm"}</L10n></StatusBadge>}</L10nValue></td>
                  <td className="px-4 py-2"><L10nValue>{department.location || "—"}</L10nValue></td>
                  <td className="px-4 py-2">
                    <StatusBadge tone={department.active ? "success" : "neutral"}>
                      <L10nValue>{department.active ? "Đang hoạt động" : "Ngừng hoạt động"}</L10nValue>
                    </StatusBadge>
                  </td>
                  <td className="px-4 py-2"><Link href={`/organization/departments/${department.departmentId}`} aria-label={localize(`Xem chi tiết ${department.departmentName}`)} className="inline-flex min-h-11 items-center rounded-lg px-2 py-2 font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"><L10n>{"Xem chi tiết"}</L10n></Link></td>
                </tr>
              ))}</L10nValue>
            </tbody>
          </table>
        </div>
      ) : null}</L10nValue>
    </DataTableShell>
  );
}
