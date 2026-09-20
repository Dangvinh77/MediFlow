"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { organizationApi } from "../api";
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

export function DepartmentList() {
  const router = useRouter();
  const [departments, setDepartments] = useState<DepartmentDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RequestError | null>(null);

  const loadDepartments = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const result = await organizationApi.departments();
      setDepartments(result);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      setError(getRequestError(cause));
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    let disposed = false;

    organizationApi
      .departments()
      .then((result) => {
        if (!disposed) {
          setDepartments(result);
        }
      })
      .catch((cause: unknown) => {
        if (disposed) return;

        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }

        setError(getRequestError(cause));
      })
      .finally(() => {
        if (!disposed) {
          setLoading(false);
        }
      });

    return () => {
      disposed = true;
    };
  }, [router]);

  return (
    <section className="mt-8" aria-labelledby="organization-departments-heading">
      <div className="mb-4">
        <h2 id="organization-departments-heading" className="text-lg font-semibold">
          Khoa phòng
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Các khoa đang hoạt động trong bệnh viện.
        </p>
      </div>

      {loading ? <AsyncState kind="loading" message="Đang tải danh sách khoa…" /> : null}

      {!loading && error ? (
        <AsyncState
          kind="error"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={() => void loadDepartments()}
        />
      ) : null}

      {!loading && !error && departments.length === 0 ? (
        <AsyncState kind="empty" message="Chưa có khoa đang hoạt động." />
      ) : null}

      {!loading && !error && departments.length > 0 ? (
        <div className="overflow-x-auto rounded-xl border border-border bg-surface">
          <table className="w-full min-w-3xl text-left text-sm">
            <thead className="border-b border-border bg-surface-muted">
              <tr>
                <th scope="col" className="px-4 py-3">Tên khoa</th>
                <th scope="col" className="px-4 py-3">Viết tắt</th>
                <th scope="col" className="px-4 py-3">Loại</th>
                <th scope="col" className="px-4 py-3">Địa điểm</th>
                <th scope="col" className="px-4 py-3">Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {departments.map((department) => (
                <tr key={department.departmentId} className="border-b border-border last:border-0">
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}
