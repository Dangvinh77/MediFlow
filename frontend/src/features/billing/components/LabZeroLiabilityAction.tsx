"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import { prepareCommandIntent, CommandIntentError } from "@/lib/command-intent";
import { getSessionKey } from "@/lib/session";
import { billingApi } from "../api";
import { clearLabZeroRecovery, readLabZeroRecovery, saveLabZeroRecovery } from "../lab-zero-recovery";
import type { LabZeroPayableEligibilityDTO } from "../types";

interface LabZeroLiabilityActionProps {
  paymentRequestId: string;
  quoteId: string;
  quoteExpiresAt: string;
  quoteCurrent: boolean;
  enabled: boolean;
  onLockChange?: (locked: boolean) => void;
  onCompleted?: (result: LabZeroPayableEligibilityDTO) => void;
}

function uncertain(cause: unknown) {
  return !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500
    || cause.code?.includes("IDEMPOTENCY_CONFLICT") === true || cause.code?.endsWith("IDEMPOTENCY_KEY_REUSED") === true;
}

export function LabZeroLiabilityAction({ paymentRequestId, quoteId, quoteExpiresAt, quoteCurrent, enabled, onLockChange, onCompleted }: LabZeroLiabilityActionProps) {
  const [retryQuoteId, setRetryQuoteId] = useState<string | null>(null);
  const [result, setResult] = useState<LabZeroPayableEligibilityDTO | null>(null);
  const [error, setError] = useState<{ message: string; correlationId: string | null; uncertain?: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const [recoveryLoading, setRecoveryLoading] = useState(true);
  const submitLock = useRef(false);
  const mounted = useRef(true);
  const originatingSession = useRef(getSessionKey());

  useEffect(() => {
    let active = true;
    mounted.current = true;
    onLockChange?.(true);
    void readLabZeroRecovery(paymentRequestId, originatingSession.current).then(recovery => {
      if (!active) return;
      if (recovery) setRetryQuoteId(recovery.quoteId);
      setRecoveryLoading(false);
      onLockChange?.(Boolean(recovery));
    }).catch(cause => {
      if (active) {
        setError({ message: cause instanceof Error ? cause.message : "Không thể đọc yêu cầu đang chờ.", correlationId: null, uncertain: true });
        setRecoveryLoading(false); onLockChange?.(true);
      }
    });
    return () => { active = false; mounted.current = false; };
  }, [paymentRequestId, onLockChange]);

  async function confirm(selectedQuoteId: string) {
    if (submitLock.current || !enabled || recoveryLoading) return;
    const actorSession = originatingSession.current;
    const current = () => mounted.current && actorSession === getSessionKey();
    if (!current()) return;
    if (!retryQuoteId && (!Number.isFinite(Date.parse(quoteExpiresAt)) || Date.parse(quoteExpiresAt) <= Date.now())) {
      setError({ message: "Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi xác nhận nghĩa vụ 0.", correlationId: null });
      return;
    }
    submitLock.current = true;
    setBusy(true);
    onLockChange?.(true);
    setError(null);
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | null = null;
    const body = { quoteId: selectedQuoteId };
    try {
      intent = await prepareCommandIntent(`lab-zero-liability:${paymentRequestId}`, actorSession, body);
      if (!current()) { intent.cancelUnsent(); return; }
      try {
        await saveLabZeroRecovery(paymentRequestId, actorSession, { quoteId: selectedQuoteId });
      } catch (cause) {
        intent.cancelUnsent();
        throw new CommandIntentError(cause instanceof Error ? cause.message : "Không thể lưu yêu cầu xác nhận đang chờ.");
      }
      if (!current()) {
        // Keep an earlier uncertain command even if this replay was never sent.
        if (!retryQuoteId) await clearLabZeroRecovery(paymentRequestId, actorSession);
        intent.cancelUnsent(); return;
      }
      const confirmed = await billingApi.confirmLabZeroLiability(paymentRequestId, body, {
        idempotencyKey: intent.key,
        correlationId: intent.correlationId,
      });
      await clearLabZeroRecovery(paymentRequestId, actorSession);
      intent.settle();
      if (!current()) return;
      setResult(confirmed);
      setRetryQuoteId(null);
      onLockChange?.(false);
      onCompleted?.(confirmed);
    } catch (cause) {
      let remainsUncertain = cause instanceof CommandIntentError || uncertain(cause);
      if (!current()) {
        if (intent) intent.settle(remainsUncertain ? cause instanceof Error ? cause : new Error("Uncertain zero-liability outcome") : cause);
        return;
      }
      if (!remainsUncertain) {
        try {
          await clearLabZeroRecovery(paymentRequestId, actorSession);
          setRetryQuoteId(null);
        } catch {
          remainsUncertain = true;
          setRetryQuoteId(selectedQuoteId);
        }
      } else {
        setRetryQuoteId(selectedQuoteId);
      }
      if (intent) intent.settle(remainsUncertain ? cause instanceof CommandIntentError ? cause : new Error("Uncertain Lab zero-liability result") : cause);
      setError({
        message: cause instanceof ApiRequestError || cause instanceof Error ? cause.message : "Chưa xác định được kết quả xác nhận.",
        correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
        uncertain: remainsUncertain,
      });
      onLockChange?.(remainsUncertain);
    } finally {
      submitLock.current = false;
      setBusy(false);
    }
  }

  if (result) return <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-3 text-sm"><L10n>{"Đã xác nhận nghĩa vụ 0 đồng."}</L10n></p>;

  return <div className="space-y-3 rounded-lg border border-border p-4">
    <p className="text-sm"><L10n>{"Xác nhận nghĩa vụ 0 đồng là bước riêng, không ghi nhận thanh toán tiền mặt."}</L10n></p>
    <L10nValue>{error ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
      <p><L10nValue>{error.message}</L10nValue></p>
      <L10nValue>{error.correlationId ? <p className="mt-1 text-xs"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{error.correlationId}</span></p> : null}</L10nValue>
      <L10nValue>{error.uncertain ? <p className="mt-2"><L10n>{"Giữ nguyên yêu cầu hiện tại. Gửi lại cùng yêu cầu để kiểm tra kết quả."}</L10n></p> : null}</L10nValue>
    </div> : null}</L10nValue>
    <L10nValue>{retryQuoteId ? <>
      <p role="status" className="text-sm text-warning"><L10n>{"Có yêu cầu xác nhận chưa rõ kết quả. Báo giá ban đầu được giữ nguyên; không tạo yêu cầu khác."}</L10n></p>
      <Button type="button" variant="primary" loading={busy} onClick={() => void confirm(retryQuoteId)}><L10n>{"Gửi lại cùng yêu cầu xác nhận 0 đồng"}</L10n></Button>
    </> : <Button type="button" variant="primary" loading={busy} disabled={!enabled || !quoteCurrent || busy || recoveryLoading || error?.uncertain === true} onClick={() => void confirm(quoteId)}><L10n>{"Xác nhận nghĩa vụ 0 đồng"}</L10n></Button>}</L10nValue>
    <L10nValue>{!enabled ? <p className="text-xs text-muted-foreground"><L10n>{"Xác nhận nghĩa vụ 0 chưa được mở."}</L10n></p> : null}</L10nValue>
    <L10nValue>{enabled && !quoteCurrent && !retryQuoteId ? <p role="alert" className="text-xs text-danger"><L10n>{"Báo giá hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi xác nhận."}</L10n></p> : null}</L10nValue>
  </div>;
}
