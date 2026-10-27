"use client";

import { useEffect, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/session";
import { CommandIntentError, prepareCommandIntent } from "@/lib/command-intent";
import { Button } from "@/components/ui/Button";
import { ReferenceText } from "@/components/references/ReferenceText";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { medicalRecordApi } from "../api";
import type { CreateFollowUpRecommendationRequest, FollowUpRecommendationDTO } from "../types";

interface FollowUpError { message: string; correlationId: string | null; uncertain?: boolean }
type RecommendationState =
  | { status: "loading" }
  | { status: "empty" }
  | { status: "loaded"; recommendation: FollowUpRecommendationDTO }
  | { status: "error"; error: FollowUpError };

function isUncertain(cause: unknown) {
  return !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

function toError(cause: unknown, fallback: string): FollowUpError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

export function FollowUpRecommendationPanel({ recordId, enabled, canCreate, patientView = false }: {
  recordId: string;
  enabled: boolean;
  canCreate: boolean;
  patientView?: boolean;
}) {
  const [state, setState] = useState<RecommendationState>({ status: enabled ? "loading" : "empty" });
  const [recommendedFrom, setRecommendedFrom] = useState("");
  const [recommendedThrough, setRecommendedThrough] = useState("");
  const [reason, setReason] = useState("");
  const [preparationInstructions, setPreparationInstructions] = useState("");
  const [error, setError] = useState<FollowUpError | null>(null);
  const [pendingBody, setPendingBody] = useState<CreateFollowUpRecommendationRequest | null>(null);
  const [saving, setSaving] = useState(false);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    let active = true;
    if (!enabled) return () => { active = false; };
    medicalRecordApi.getFollowUpRecommendation(recordId)
      .then(recommendation => { if (active) setState({ status: "loaded", recommendation }); })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 404 && cause.code === "RESOURCE_NOT_FOUND") {
          setState({ status: "empty" });
          return;
        }
        setState({ status: "error", error: toError(cause, "Không thể tải đề xuất tái khám.") });
      });
    return () => { active = false; };
  }, [enabled, recordId, retryToken]);

  async function submit() {
    if (!enabled || !canCreate || saving || state.status !== "empty") return;
    const body = pendingBody && error?.uncertain ? pendingBody : {
      recommendedFrom,
      recommendedThrough,
      reason: reason.trim(),
      preparationInstructions: preparationInstructions.trim() || null,
    };
    if (!/^\d{4}-\d{2}-\d{2}$/.test(body.recommendedFrom) || !/^\d{4}-\d{2}-\d{2}$/.test(body.recommendedThrough)) {
      setError({ message: "Chọn ngày bắt đầu và ngày kết thúc khuyến nghị.", correlationId: null });
      return;
    }
    if (body.recommendedThrough < body.recommendedFrom) {
      setError({ message: "Ngày kết thúc không được trước ngày bắt đầu.", correlationId: null });
      return;
    }
    if (!body.reason.trim()) {
      setError({ message: "Nhập lý do tái khám.", correlationId: null });
      return;
    }
    if (body.reason.length > 1000 || (body.preparationInstructions?.length ?? 0) > 2000) {
      setError({ message: "Lý do tối đa 1000 ký tự, hướng dẫn chuẩn bị tối đa 2000 ký tự.", correlationId: null });
      return;
    }

    setSaving(true); setError(null); setPendingBody(body);
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | null = null;
    try {
      intent = await prepareCommandIntent(`follow-up-recommendation:${recordId}`, getSessionKey(), body);
      const recommendation = await medicalRecordApi.createFollowUpRecommendation(recordId, body, {
        idempotencyKey: intent.key,
        correlationId: intent.correlationId,
      });
      intent.settle();
      setPendingBody(null);
      setState({ status: "loaded", recommendation });
    } catch (cause) {
      if (intent) intent.settle(cause);
      const uncertain = isUncertain(cause) || cause instanceof CommandIntentError;
      if (!uncertain) setPendingBody(null);
      setError({ ...toError(cause, "Không thể tạo đề xuất tái khám."), uncertain });
    } finally {
      setSaving(false);
    }
  }

  if (!enabled) return null;
  if (state.status === "loading") return <section className="rounded-xl border border-border bg-surface p-6"><p role="status" className="text-sm text-muted-foreground">Đang tải đề xuất tái khám…</p></section>;
  if (state.status === "error") return <section className="rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger"><p role="alert">{state.error.message}</p>{state.error.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: <span className="font-mono">{state.error.correlationId}</span></p> : null}<Button type="button" variant="secondary" className="mt-3" onClick={() => { setState({ status: "loading" }); setRetryToken(value => value + 1); }}>Tải lại đề xuất</Button></section>;
  if (state.status === "loaded") {
    const recommendation = state.recommendation;
    return <section className="space-y-4 rounded-xl border border-border bg-surface p-6">
      <div><h2 className="text-lg font-semibold">Đề xuất tái khám</h2><p className="mt-1 text-sm text-muted-foreground">Đề xuất đã lưu và không thể chỉnh sửa.</p></div>
      <dl className="grid gap-4 sm:grid-cols-2">
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Khoảng ngày đề xuất</dt><dd className="mt-1 text-sm">{formatLocalDate(recommendation.recommendedFrom)} – {formatLocalDate(recommendation.recommendedThrough)}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Bác sĩ ưu tiên</dt><dd className="mt-1"><ReferenceText kind="doctor" value={recommendation.preferredDoctorId} fallback="Bác sĩ ưu tiên" /></dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Ngày tạo</dt><dd className="mt-1 text-sm">{formatInstant(recommendation.createdAt)}</dd></div>
      </dl>
      {patientView ? <p className="text-sm text-muted-foreground">Liên hệ điều dưỡng để chọn buổi khám phù hợp.</p> : null}
      <details className="rounded-lg border border-border px-4 py-3 text-sm">
        <summary className="min-h-8 cursor-pointer font-medium underline underline-offset-2">Thông tin đối chiếu</summary>
        <dl className="mt-3"><div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Lượt khám nguồn</dt><dd className="mt-1 break-all font-mono">{recommendation.sourceRegistrationId}</dd></div></dl>
      </details>
      <div><h3 className="text-sm font-semibold">Lý do</h3><p className="mt-1 whitespace-pre-wrap text-sm">{recommendation.reason}</p></div>
      {recommendation.preparationInstructions ? <div><h3 className="text-sm font-semibold">Hướng dẫn chuẩn bị</h3><p className="mt-1 whitespace-pre-wrap text-sm">{recommendation.preparationInstructions}</p></div> : null}
    </section>;
  }

  return <section className="space-y-4 rounded-xl border border-border bg-surface p-6">
    <div><h2 className="text-lg font-semibold">Đề xuất tái khám</h2><p className="mt-1 text-sm text-muted-foreground">Một đề xuất bất biến cho hồ sơ V2 đã hoàn tất. Khoảng ngày là khuyến nghị; đăng ký ngoài khoảng sẽ được cảnh báo và không tự động từ chối.</p></div>
    <p role="status" className="text-sm text-muted-foreground">Chưa có đề xuất tái khám.</p>
    {canCreate ? <form className="grid gap-4 sm:grid-cols-2" onSubmit={event => { event.preventDefault(); void submit(); }}>
      <label className="space-y-1 text-sm font-medium" htmlFor="follow-up-from">Từ ngày khuyến nghị
        <input id="follow-up-from" type="date" value={recommendedFrom} onChange={event => { setRecommendedFrom(event.target.value); setError(null); }} disabled={saving || Boolean(error?.uncertain)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" />
      </label>
      <label className="space-y-1 text-sm font-medium" htmlFor="follow-up-through">Đến ngày khuyến nghị
        <input id="follow-up-through" type="date" value={recommendedThrough} onChange={event => { setRecommendedThrough(event.target.value); setError(null); }} disabled={saving || Boolean(error?.uncertain)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" />
      </label>
      <label className="space-y-1 text-sm font-medium sm:col-span-2" htmlFor="follow-up-reason">Lý do tái khám
        <textarea id="follow-up-reason" value={reason} maxLength={1000} onChange={event => { setReason(event.target.value); setError(null); }} disabled={saving || Boolean(error?.uncertain)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2" />
      </label>
      <label className="space-y-1 text-sm font-medium sm:col-span-2" htmlFor="follow-up-preparation">Hướng dẫn chuẩn bị (không bắt buộc)
        <textarea id="follow-up-preparation" value={preparationInstructions} maxLength={2000} onChange={event => { setPreparationInstructions(event.target.value); setError(null); }} disabled={saving || Boolean(error?.uncertain)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2" />
      </label>
      {error ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger sm:col-span-2"><p>{error.message}</p>{error.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: <span className="font-mono">{error.correlationId}</span></p> : null}{error.uncertain ? <p className="mt-2">Chưa xác định đã lưu đề xuất hay chưa. Gửi lại đúng lệnh để dùng cùng khóa idempotency.</p> : null}</div> : null}
      <div className="sm:col-span-2"><Button type="submit" loading={saving}>{error?.uncertain ? "Gửi lại cùng đề xuất" : "Tạo đề xuất tái khám"}</Button></div>
    </form> : <p className="text-sm text-muted-foreground">Chưa có đề xuất tái khám cho hồ sơ này.</p>}
  </section>;
}
