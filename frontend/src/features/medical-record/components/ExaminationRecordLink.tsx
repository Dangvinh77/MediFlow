"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { medicalRecordApi } from "../api";

export function ExaminationRecordLink({ patientId, appointmentId, label = "Mở hồ sơ khám" }: { patientId: string; appointmentId: string; label?: string }) {
  const { localize } = useLocale();
  const router = useRouter();
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState<{ key: string; recordId?: string; message?: string; correlationId?: string | null; forbidden?: boolean }>({ key: "" });
  const key = `${patientId}:${appointmentId}:${retry}`;
  useEffect(() => {
    let active = true;
    medicalRecordApi.byPatient(patientId).then((records) => {
      if (!active) return;
      const matching = records.filter((record) => record.appointmentId === appointmentId);
      setState(matching.length === 1 ? { key, recordId: matching[0].recordId } : { key, message: "Chưa xác định được một hồ sơ duy nhất cho lịch hẹn. Hãy cập nhật hoặc tra cứu lại." });
    }).catch((cause: unknown) => {
      if (!active) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      if (cause instanceof ApiRequestError && cause.status === 403) { setState({ key, forbidden: true }); return; }
      setState({ key, message: cause instanceof Error ? cause.message : "Không thể tìm hồ sơ khám.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    });
    return () => { active = false; };
  }, [appointmentId, key, patientId, router]);
  if (state.key !== key) return <AsyncState kind="loading" message={localize("Đang tìm hồ sơ của lịch hẹn…")} />;
  if (state.forbidden) return <p role="status" className="text-sm text-muted-foreground"><L10n>{"Bạn không có quyền xem hồ sơ khám của lịch hẹn này."}</L10n></p>;
  return <div className="flex flex-wrap items-center gap-2">
    <L10nValue>{state.recordId ? <Link className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground" href={`/records/${state.recordId}`}><L10nValue>{label}</L10nValue></Link>
      : <div><AsyncState kind="error" message={localize(state.message ?? "Không thể tìm hồ sơ khám.")} onRetry={() => setRetry((value) => value + 1)} /><L10nValue>{state.correlationId ? <details className="mt-2 text-xs text-muted-foreground"><summary className="min-h-8 cursor-pointer underline underline-offset-4"><L10n>{"Thông tin hỗ trợ"}</L10n></summary><p className="mt-1 break-all font-mono"><L10n>{"Mã theo dõi: "}</L10n>{state.correlationId}</p></details> : null}</L10nValue></div>}</L10nValue>
  </div>;
}
