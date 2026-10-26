"use client";

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
}

function uncertain(cause: unknown) {
  return !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500
    || cause.code?.includes("IDEMPOTENCY_CONFLICT") === true || cause.code?.endsWith("IDEMPOTENCY_KEY_REUSED") === true;
}

export function LabZeroLiabilityAction({ paymentRequestId, quoteId, quoteExpiresAt, quoteCurrent, enabled }: LabZeroLiabilityActionProps) {
  const [retryQuoteId, setRetryQuoteId] = useState<string | null>(null);
  const [result, setResult] = useState<LabZeroPayableEligibilityDTO | null>(null);
  const [error, setError] = useState<{ message: string; correlationId: string | null; uncertain?: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const submitLock = useRef(false);

  useEffect(() => {
    let active = true;
    void readLabZeroRecovery(paymentRequestId, getSessionKey()).then(recovery => {
      if (active && recovery) setRetryQuoteId(recovery.quoteId);
    }).catch(cause => {
      if (active) setError({ message: cause instanceof Error ? cause.message : "Không thể đọc yêu cầu đang chờ.", correlationId: null, uncertain: true });
    });
    return () => { active = false; };
  }, [paymentRequestId]);

  async function confirm(selectedQuoteId: string) {
    if (submitLock.current || !enabled) return;
    if (!retryQuoteId && (!Number.isFinite(Date.parse(quoteExpiresAt)) || Date.parse(quoteExpiresAt) <= Date.now())) {
      setError({ message: "Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi xác nhận nghĩa vụ 0.", correlationId: null });
      return;
    }
    submitLock.current = true;
    setBusy(true);
    setError(null);
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | null = null;
    const body = { quoteId: selectedQuoteId };
    try {
      intent = await prepareCommandIntent(`lab-zero-liability:${paymentRequestId}`, getSessionKey(), body);
      try {
        await saveLabZeroRecovery(paymentRequestId, getSessionKey(), { quoteId: selectedQuoteId });
      } catch (cause) {
        intent.cancelUnsent();
        throw new CommandIntentError(cause instanceof Error ? cause.message : "Không thể lưu yêu cầu xác nhận đang chờ.");
      }
      const confirmed = await billingApi.confirmLabZeroLiability(paymentRequestId, body, {
        idempotencyKey: intent.key,
        correlationId: intent.correlationId,
      });
      await clearLabZeroRecovery(paymentRequestId, getSessionKey());
      intent.settle();
      setResult(confirmed);
      setRetryQuoteId(null);
    } catch (cause) {
      let remainsUncertain = cause instanceof CommandIntentError || uncertain(cause);
      if (!remainsUncertain) {
        try {
          await clearLabZeroRecovery(paymentRequestId, getSessionKey());
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
    } finally {
      submitLock.current = false;
      setBusy(false);
    }
  }

  if (result) return <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-3 text-sm">
    Đã xác nhận nghĩa vụ 0 đồng. Mã xác nhận: <span className="font-mono">{result.clearanceId}</span>
  </p>;

  return <div className="space-y-3 rounded-lg border border-border p-4">
    <p className="text-sm">Xác nhận nghĩa vụ 0 đồng là bước riêng, không ghi nhận thanh toán tiền mặt.</p>
    {error ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
      <p>{error.message}</p>
      {error.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: <span className="font-mono">{error.correlationId}</span></p> : null}
      {error.uncertain ? <p className="mt-2">Giữ nguyên yêu cầu hiện tại. Gửi lại cùng yêu cầu để kiểm tra kết quả.</p> : null}
    </div> : null}
    {retryQuoteId ? <>
      <p role="status" className="text-sm text-warning">Có yêu cầu xác nhận chưa rõ kết quả. Báo giá ban đầu được giữ nguyên; không tạo yêu cầu khác.</p>
      <Button type="button" variant="primary" loading={busy} onClick={() => void confirm(retryQuoteId)}>Gửi lại cùng yêu cầu xác nhận 0 đồng</Button>
    </> : <Button type="button" variant="primary" loading={busy} disabled={!enabled || !quoteCurrent || busy || error?.uncertain === true} onClick={() => void confirm(quoteId)}>
      Xác nhận nghĩa vụ 0 đồng
    </Button>}
    {!enabled ? <p className="text-xs text-muted-foreground">Xác nhận nghĩa vụ 0 chưa được mở.</p> : null}
    {enabled && !quoteCurrent && !retryQuoteId ? <p role="alert" className="text-xs text-danger">Báo giá hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi xác nhận.</p> : null}
  </div>;
}
