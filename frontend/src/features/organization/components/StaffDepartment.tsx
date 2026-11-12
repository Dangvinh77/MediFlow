"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


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
  return <div><dt className="text-sm text-muted-foreground"><L10n>{"Đơn vị công tác"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{department ? <><span>{department.departmentName}</span><L10nValue>{canOpen ? <Link href={`/organization/departments/${departmentId}`} className="ml-2 font-semibold text-primary hover:underline"><L10n>{"Xem chi tiết"}</L10n></Link> : null}</L10nValue></>
    : failed ? <span><L10n>{"Chưa tải được tên đơn vị. "}</L10n><button type="button" className="text-primary underline" onClick={() => { setFailed(false); setAttempt(value => value + 1); }}><L10n>{"Thử lại"}</L10n></button></span>
    : "Đang tải tên đơn vị…"}</L10nValue></dd></div>;
}
