"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { useEffect, useState } from "react";
import { getSessionKey } from "@/lib/auth";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import type { DepartmentDTO } from "../types";

export function StaffDepartment({ departmentId, canOpen = true }: { departmentId: string; canOpen?: boolean }) {
  const router = useRouter();
  const [department, setDepartment] = useState<DepartmentDTO | null>(null);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    const session = getSessionKey();
    Promise.resolve().then(() => organizationApi.department(departmentId)).then(value => {
      if (active && isRequestSessionCurrent(session)) setDepartment(value);
    }).catch((cause: unknown) => { if (!active || !isRequestSessionCurrent(session, cause)) return; if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setFailed(true); });
    return () => { active = false; };
  }, [departmentId, attempt, router]);
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Đơn vị công tác</dt><dd className="mt-1 text-sm">{department ? <><span>{department.departmentName}</span>{canOpen ? <Link href={`/organization/departments/${departmentId}`} className="ml-2 font-semibold text-primary hover:underline">Detail</Link> : null}</>
    : failed ? <span>Chưa tải được tên đơn vị. <button type="button" className="text-primary underline" onClick={() => { setFailed(false); setAttempt(value => value + 1); }}>Thử lại</button></span>
    : "Đang tải tên đơn vị…"}</dd></div>;
}
