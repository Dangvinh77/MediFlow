"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getSessionKey } from "@/lib/session";
import { formatLocalDate } from "@/lib/format";
import { patientApi } from "../api";

export function AccountPatientLink({ patientId }: { patientId: string }) {
  const [state, setState] = useState<{ label?: string; error?: boolean }>({});
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    const session = getSessionKey();
    patientApi.getById(patientId).then(patient => {
      if (active && getSessionKey() === session) setState({ label: `${patient.hoTen} · ${formatLocalDate(patient.ngaySinh)}` });
    }).catch(() => { if (active && getSessionKey() === session) setState({ error: true }); });
    return () => { active = false; };
  }, [patientId, attempt]);
  return <><Link className="text-primary underline" href={`/patients/${encodeURIComponent(patientId)}`}>Bệnh nhân: {state.label ?? (state.error ? "Chưa tải được tên" : "Đang tải tên…")}</Link>{state.error ? <button type="button" className="ml-2 text-primary underline" onClick={() => { setState({}); setAttempt(value => value + 1); }}>Tải lại tên bệnh nhân</button> : null}</>;
}
