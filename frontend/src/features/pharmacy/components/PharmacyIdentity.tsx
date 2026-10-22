"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useReferenceDirectory } from "@/components/references/ReferenceProvider";
import { api, ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/auth";
import { getSessionKey } from "@/lib/session";
import { isUuid } from "../utils";

type Kind = "patient" | "doctor" | "department";
type Result = { name: string } | { error: string };
type Lookup = (kind: Kind, id: string, attempt: number) => Promise<Result>;
const IdentityContext = createContext<Lookup | null>(null);

/** Only the public human APIs; never substitute account IDs or service-only lookups. */
async function readName(kind: Kind, id: string): Promise<string> {
  if (kind === "patient") {
    const patient = await api.get<{ maBenhNhan: string; hoTen: string }>(`/v1/patients/${encodeURIComponent(id)}`);
    if (patient.maBenhNhan.toLowerCase() === id.toLowerCase() && patient.hoTen.trim()) return patient.hoTen;
  } else if (kind === "doctor") {
    const staff = await api.get<{ staffId: string; fullName: string }>(`/v1/org/staff/${encodeURIComponent(id)}`);
    if (staff.staffId.toLowerCase() === id.toLowerCase() && staff.fullName.trim()) return staff.fullName;
  } else {
    const department = await api.get<{ departmentId: string; departmentName: string }>(`/v1/org/departments/${encodeURIComponent(id)}`);
    if (department.departmentId.toLowerCase() === id.toLowerCase() && department.departmentName.trim()) return department.departmentName;
  }
  throw new Error("Identity response does not match the requested resource");
}

/** Mounted inside the keyed Pharmacy boundary: private cache dies with the login session. */
export function PharmacyIdentityProvider({ children }: { children: ReactNode }) {
  const directory = useReferenceDirectory();
  const [lookup] = useState<Lookup>(() => {
    const session = getSessionKey();
    const role = getRole();
    const cache = new Map<string, Promise<Result>>();
    return (kind: Kind, id: string, attempt: number) => {
      if (!session || getSessionKey() !== session || getRole() !== role) return Promise.resolve({ error: "Phiên tra cứu đã thay đổi" });
      if (role !== "ADMIN" && role !== "DOCTOR") return Promise.resolve({ error: "Tài khoản chưa được cấp quyền tra cứu tên" });
      const key = `${kind}:${id.toLowerCase()}:${attempt}`;
      let request = cache.get(key);
      if (!request) {
        request = (directory ? directory.resolve(kind, id).then(name => {
          if (!name?.trim()) throw new Error("Identity label unavailable");
          return name;
        }) : readName(kind, id)).then(name => ({ name }), (cause: unknown) => {
          return { error: cause instanceof ApiRequestError && cause.status === 403 ? "Không có quyền tra cứu tên"
            : cause instanceof ApiRequestError && cause.status === 404 ? "Không tìm thấy thông tin tên" : "Chưa tải được tên" };
        });
        cache.set(key, request);
      }
      return request;
    };
  });
  return <IdentityContext.Provider value={lookup}>{children}</IdentityContext.Provider>;
}

export function PharmacyIdentity({ kind, id, showId = true }: { kind: Kind; id: string | null; showId?: boolean }) {
  const lookup = useContext(IdentityContext);
  const [attempt, setAttempt] = useState(0);
  const key = `${kind}:${id}:${attempt}`;
  const [resolved, setResolved] = useState<{ key: string; result: Result } | null>(null);
  useEffect(() => {
    if (!id || !isUuid(id) || !lookup) return;
    let active = true;
    // A complete UUID may be pasted/edited in a form; coalesce editing before issuing a lookup.
    const timer = setTimeout(() => { void lookup(kind, id, attempt).then(result => { if (active) setResolved({ key, result }); }); }, 300);
    return () => { active = false; clearTimeout(timer); };
  }, [id, kind, key, lookup, attempt]);
  if (!id || !isUuid(id)) return null;
  const result = resolved?.key === key ? resolved.result : null;
  return <span className="block min-w-0 text-sm" aria-live="polite">
    <span className="block break-words">{result && "name" in result ? result.name : result && "error" in result ? result.error : "Đang tra cứu tên…"}</span>
    {showId && <code className="block break-all text-xs text-muted-foreground">{id}</code>}
    {result && "error" in result && result.error === "Chưa tải được tên" && <button type="button" onClick={() => setAttempt(value => value + 1)} className="min-h-11 text-primary underline focus-visible:outline-2 focus-visible:outline-primary">Tra cứu tên lại</button>}
  </span>;
}
