"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useReferenceDirectory } from "@/components/references/ReferenceProvider";
import { api, ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/auth";
import { getSessionKey } from "@/lib/session";

type Result = { name: string } | { error: string };
type Lookup = (id: string, attempt: number) => Promise<Result>;
const DepartmentContext = createContext<Lookup | null>(null);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Reuses the dashboard directory; standalone views retain their public API fallback. */
export function ReportDepartmentProvider({ children }: { children: ReactNode }) {
  const directory = useReferenceDirectory();
  const [lookup] = useState<Lookup>(() => {
    const session = getSessionKey();
    const role = getRole();
    const cache = new Map<string, Promise<Result>>();
    return (id: string, attempt: number) => {
      if (!session || getSessionKey() !== session || getRole() !== role || (role !== "ADMIN" && role !== "MANAGER")) return Promise.resolve({ error: "Không có quyền tra cứu tên khoa" });
      const key = `${id.toLowerCase()}:${attempt}`;
      let request = cache.get(key);
      if (!request) {
        request = (directory ? directory.resolve("department", id).then(name => {
          if (!name?.trim()) throw new Error("Department label unavailable");
          return { name };
        }) : api.get<{ departmentId: string; departmentName: string }>(`/v1/org/departments/${encodeURIComponent(id)}`).then(department => {
          if (department.departmentId.toLowerCase() !== id.toLowerCase() || !department.departmentName.trim()) throw new Error("Department response mismatch");
          return { name: department.departmentName };
        })).catch((cause: unknown) => {
          return { error: cause instanceof ApiRequestError && cause.status === 403 ? "Không có quyền tra cứu tên khoa"
            : cause instanceof ApiRequestError && cause.status === 404 ? "Không tìm thấy tên khoa" : "Chưa tải được tên khoa" };
        });
        cache.set(key, request);
      }
      return request;
    };
  });
  return <DepartmentContext.Provider value={lookup}>{children}</DepartmentContext.Provider>;
}

export function ReportDepartmentName({ id, showId = true }: { id?: string | null; showId?: boolean }) {
  const lookup = useContext(DepartmentContext);
  const [attempt, setAttempt] = useState(0);
  const [resolved, setResolved] = useState<{ key: string; result: Result } | null>(null);
  const key = `${id}:${attempt}`;
  useEffect(() => {
    if (!id || !uuid.test(id) || !lookup) return;
    let active = true;
    const timer = setTimeout(() => { void lookup(id, attempt).then(result => { if (active) setResolved({ key, result }); }); }, 300);
    return () => { active = false; clearTimeout(timer); };
  }, [id, lookup, attempt, key]);
  if (!id) return <span>Toàn bệnh viện</span>;
  if (!uuid.test(id)) return <span className="break-all">{id}</span>;
  const result = resolved?.key === key ? resolved.result : null;
  return <span className="inline-block align-top" aria-live="polite">
    <span className="block break-words">{result && "name" in result ? result.name : result && "error" in result ? result.error : "Đang tra cứu tên khoa…"}</span>
    {showId && <code className="block break-all text-xs text-muted-foreground">{id}</code>}
    {result && "error" in result && result.error === "Chưa tải được tên khoa" && <button type="button" onClick={() => setAttempt(value => value + 1)} className="min-h-11 text-primary underline focus-visible:outline-2 focus-visible:outline-primary">Tra cứu tên khoa lại</button>}
  </span>;
}
