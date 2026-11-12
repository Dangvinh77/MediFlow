"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/auth";
import { formatLocalDate } from "@/lib/format";
import { patientApi } from "../api";
import type { PatientDTO } from "../types";
import { DetailSection, RecordIdentity } from "./RecordLayout";

/** Read-only self profile; the route remounts this component on session/identity change. */
export function PersonalPatientProfile({ patientId }: { patientId: string }) {
  const { localize } = useLocale();
  const router = useRouter();
  const [patient, setPatient] = useState<PatientDTO | null>(null);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    const session = getSessionKey();
    patientApi.getById(patientId).then((value) => {
      if (active && session === getSessionKey()) setPatient(value);
    }).catch((cause: unknown) => {
      const current = getSessionKey();
      if (!active || (session !== current && !(current === null && cause instanceof ApiRequestError && cause.status === 401))) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setError({ message: cause instanceof Error ? cause.message : "Không thể tải hồ sơ cá nhân.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    });
    return () => { active = false; };
  }, [patientId, attempt, router]);
  if (error) return <AsyncState kind="error" {...error} onRetry={() => { setError(null); setAttempt((value) => value + 1); }} />;
  if (!patient) return <AsyncState kind="loading" message={localize("Đang tải hồ sơ cá nhân…")} />;
  return <div className="space-y-5">
    <RecordIdentity name={patient.hoTen} subtitle="Hồ sơ bệnh nhân của bạn" />
    <div className="grid items-start gap-5 xl:grid-cols-2">
      <DetailSection title={localize("Thông tin cá nhân")}><Info label={localize("Ngày sinh")} value={formatLocalDate(patient.ngaySinh)} /><Info label={localize("Giới tính")} value={patient.gioiTinh === "M" ? "Nam" : "Nữ"} /><Info label={localize("CCCD")} value={patient.soCmnd} /><Info label={localize("BHYT")} value={patient.bhytSo} /></DetailSection>
      <DetailSection title={localize("Thông tin liên hệ")}><Info label={localize("Điện thoại")} value={patient.soDienThoai} /><Info label={localize("Email")} value={patient.email} /><Info label={localize("Địa chỉ")} value={patient.diaChi} /></DetailSection>
    </div>
    <p className="text-sm text-muted-foreground"><L10n>{"Nếu cần cập nhật hồ sơ, vui lòng liên hệ bộ phận tiếp nhận."}</L10n></p>
  </div>;
}

function Info({ label, value }: { label: string; value: string | null }) {
  return <div><dt className="text-sm text-muted-foreground"><L10nValue>{label}</L10nValue></dt><dd className="mt-1.5 break-words text-sm leading-6"><L10nValue>{value ?? "—"}</L10nValue></dd></div>;
}
