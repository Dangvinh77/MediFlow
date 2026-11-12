"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useEffect, useState } from "react";
import { getSessionKey } from "@/lib/auth";
import { organizationApi } from "../api";

export function AccountStaffLink({ staffId }: { staffId: string }) {
  const [state, setState] = useState<{ name?: string; error?: boolean }>({});
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    const session = getSessionKey();
    organizationApi.staffMember(staffId).then(staff => {
      if (active && getSessionKey() === session) setState({ name: staff.fullName });
    }).catch(() => { if (active && getSessionKey() === session) setState({ error: true }); });
    return () => { active = false; };
  }, [staffId, attempt]);
  return <><Link className="text-primary underline" href={`/organization/staff/${encodeURIComponent(staffId)}`}><L10n>{"Nhân viên: "}</L10n><L10nValue>{state.name ?? (state.error ? "Chưa tải được tên" : "Đang tải tên…")}</L10nValue></Link><L10nValue>{state.error ? <button type="button" className="ml-2 text-primary underline" onClick={() => { setState({}); setAttempt(value => value + 1); }}><L10n>{"Tải lại tên nhân viên"}</L10n></button> : null}</L10nValue></>;
}
