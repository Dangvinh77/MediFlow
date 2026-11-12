"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import { useEffect, useState } from "react";
import { getSessionKey } from "@/lib/auth";
import { organizationApi } from "../api";

export function OwnStaffDepartment({ staffId }: { staffId: string }) {
  const [state, setState] = useState<{ name?: string; error?: string }>({});
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    const session = getSessionKey();
    organizationApi.ownDepartment(staffId).then(department => {
      if (active && getSessionKey() === session) setState({ name: department.departmentName });
    }).catch((cause: unknown) => {
      if (active && getSessionKey() === session) setState({ error: cause instanceof Error ? cause.message : "Chưa tải được đơn vị." });
    });
    return () => { active = false; };
  }, [staffId, attempt]);
  return <div><dt className="text-sm text-muted-foreground"><L10n>{"Khoa/phòng công tác"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{state.name ?? (state.error ? <><span><L10nValue>{state.error}</L10nValue></span> <button type="button" className="text-primary underline" onClick={() => { setState({}); setAttempt(value => value + 1); }}><L10n>{"Tải lại đơn vị"}</L10n></button></> : "Đang tải đơn vị…")}</L10nValue></dd></div>;
}
