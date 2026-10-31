"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import { medicalRecordApi } from "../api";

export function ExaminationRecordLink({ patientId, appointmentId }: { patientId: string; appointmentId: string }) {
  const router = useRouter();
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState<{ key: string; recordId?: string; message?: string; correlationId?: string | null }>({ key: "" });
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
      setState({ key, message: cause instanceof Error ? cause.message : "Không thể tìm hồ sơ khám.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    });
    return () => { active = false; };
  }, [appointmentId, key, patientId, router]);
  if (state.key !== key) return <AsyncState kind="loading" message="Đang tìm hồ sơ của lịch hẹn…" />;
  return <div className="flex flex-wrap items-center gap-2">
    {state.recordId ? <Link className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground" href={`/records/${state.recordId}`}>Mở hồ sơ khám</Link>
      : <AsyncState kind="error" message={state.message ?? "Không thể tìm hồ sơ khám."} correlationId={state.correlationId} onRetry={() => setRetry((value) => value + 1)} />}
    {state.recordId ? <Button type="button" onClick={() => setRetry((value) => value + 1)}>Tìm lại hồ sơ</Button> : null}
  </div>;
}
