"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { ApiRequestError, type MutationOptions } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { prepareCommandIntent } from "@/lib/command-intent";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import { useCommand } from "@/lib/use-command";

export type PanelLoadState<T> =
  | { status: "loading" }
  | { status: "empty" }
  | { status: "forbidden" }
  | { status: "hidden" }
  | { status: "error"; message: string; correlationId: string | null }
  | { status: "loaded"; data: T };

export function usePanelResource<T>(key: string, enabled: boolean, read: () => Promise<T>, isEmpty: (value: T) => boolean) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  const resourceKey = `${key}:${session ?? "anonymous"}`;
  const [snapshot, setSnapshot] = useState<{ key: string; state: PanelLoadState<T> }>(() => ({ key: resourceKey, state: { status: enabled ? "loading" : "hidden" } }));
  const state: PanelLoadState<T> = !enabled ? { status: "hidden" } : snapshot.key === resourceKey ? snapshot.state : { status: "loading" };
  const [retryToken, setRetryToken] = useState(0);
  const updateState = useCallback((next: PanelLoadState<T>) => setSnapshot({ key: resourceKey, state: next }), [resourceKey]);
  useEffect(() => {
    let active = true;
    if (!enabled) return () => { active = false; };
    read().then((data) => { if (active) updateState(isEmpty(data) ? { status: "empty" } : { status: "loaded", data }); })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 404) updateState({ status: "hidden" });
        else if (cause instanceof ApiRequestError && cause.status === 403) updateState({ status: "forbidden" });
        else updateState({ status: "error", message: cause instanceof ApiRequestError ? cause.message : cause instanceof Error ? cause.message : "Không thể tải dữ liệu.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
      });
    return () => { active = false; };
  }, [enabled, isEmpty, key, read, retryToken, session, updateState]);
  return { state, retry: () => { updateState({ status: "loading" }); setRetryToken((value) => value + 1); } };
}

export function PanelReadState({ label, state, loadingMessage, deniedMessage, retryMessage, emptyMessage, onRetry }: {
  label: string; state: PanelLoadState<unknown>; loadingMessage: string; deniedMessage: string; retryMessage: string; emptyMessage: string; onRetry: () => void;
}) {
  const { localize } = useLocale();
  if (state.status === "hidden" || state.status === "loaded") return null;
  if (state.status === "loading") return <section role="region" aria-label={localize(label)} className="rounded-xl border border-border bg-surface p-5"><AsyncState kind="loading" message={localize(loadingMessage)} /></section>;
  if (state.status === "empty") return <p role="status" className="text-sm text-muted-foreground"><L10nValue>{emptyMessage}</L10nValue></p>;
  if (state.status === "forbidden") return <p role="status" className="text-sm text-muted-foreground"><L10nValue>{deniedMessage}</L10nValue></p>;
  return <div role="region" aria-label={localize(label)} className="space-y-2 rounded-xl border border-border bg-surface p-5"><p role="alert" className="text-sm text-danger"><L10nValue>{state.message || retryMessage}</L10nValue></p><L10nValue>{state.correlationId ? <details className="text-xs text-muted-foreground"><summary className="min-h-8 cursor-pointer underline underline-offset-4"><L10n>{"Thông tin hỗ trợ"}</L10n></summary><p className="break-all font-mono"><L10n>{"Mã theo dõi: "}</L10n>{state.correlationId}</p></details> : null}</L10nValue><Button type="button" variant="secondary" onClick={onRetry}><L10n>{"Thử lại"}</L10n></Button></div>;
}

export function formatClinicalInstant(value: string | null | undefined): string {
  if (!value) return "—"; const date = new Date(value); if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh" }).format(date);
}

export interface IdempotentActionProps<T> {
  scope: string; label: string; body: unknown; disabled?: boolean;
  run: (options: Required<Pick<MutationOptions, "idempotencyKey" | "correlationId">>) => Promise<T>;
  onSuccess: (result: T) => void; onLockedChange?: (locked: boolean) => void;
}

/** Unknown outcomes freeze the payload and remount useCommand before retry. */
export function IdempotentAction<T>(props: IdempotentActionProps<T>) {
  const [attempt, setAttempt] = useState(0); const [uncertainBody, setUncertainBody] = useState<unknown>(undefined);
  const uncertain = uncertainBody !== undefined;
  return <IdempotentActionAttempt key={attempt} {...props} disabled={uncertain ? false : props.disabled}
    body={uncertain ? uncertainBody : props.body} retrying={uncertain} autoSubmit={attempt > 0 && uncertain}
    onRetry={() => { setAttempt((value) => value + 1); }}
    onSuccess={(value) => { setUncertainBody(undefined); props.onLockedChange?.(false); props.onSuccess(value); }}
    onUncertain={(body) => { setUncertainBody(body); props.onLockedChange?.(true); }}
    onDefinitive={() => { if (uncertain) setUncertainBody(undefined); props.onLockedChange?.(false); }} />;
}

function IdempotentActionAttempt<T>({ scope, label, body, disabled = false, run, onSuccess, onLockedChange, retrying, autoSubmit, onRetry, onUncertain, onDefinitive }: IdempotentActionProps<T> & { retrying: boolean; autoSubmit: boolean; onRetry: () => void; onUncertain: (body: unknown) => void; onDefinitive: () => void }) {
  const command = useCommand();
  async function submit() {
    if ((disabled && !retrying) || command.busy || command.uncertain) return;
    const session = getSessionKey();
    onLockedChange?.(true);
    let unknownOutcome = false; let succeeded = false;
    await command.run(async () => {
      const intent = await prepareCommandIntent(scope, session, body);
      if (getSessionKey() !== session) {
        intent.cancelUnsent();
        throw new ApiRequestError("Phiên đăng nhập đã thay đổi. Hãy kiểm tra lại nội dung trước khi gửi.", 401);
      }
      try { const value = await run({ idempotencyKey: intent.key, correlationId: intent.correlationId }); intent.settle(); return value; }
      catch (cause) { intent.settle(cause); unknownOutcome = !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500; throw cause; }
    }, (value) => { succeeded = true; onSuccess(value); });
    if (getSessionKey() !== session) return;
    if (unknownOutcome) onUncertain(body);
    else if (!succeeded) onDefinitive();
  }
  const submitRef = useRef(submit);
  useEffect(() => { submitRef.current = submit; }, [submit]);
  useEffect(() => { if (autoSubmit) void submitRef.current(); }, [autoSubmit]);
  return <div className="space-y-2"><Button type="button" loading={command.busy} disabled={(disabled && !retrying) || command.busy || command.uncertain} onClick={() => void submit()}><L10nValue>{label}</L10nValue></Button>
    <L10nValue>{command.error ? <div role="alert" className="text-sm text-danger"><p><L10nValue>{command.error.message}</L10nValue></p><L10nValue>{command.error.correlationId ? <p className="mt-1 break-all font-mono text-xs"><L10n>{"Mã theo dõi: "}</L10n>{command.error.correlationId}</p> : null}</L10nValue><L10nValue>{command.uncertain ? <p className="mt-1"><L10n>{"Chưa xác định kết quả. Bạn có thể gửi lại đúng nội dung yêu cầu trước."}</L10n></p> : null}</L10nValue></div> : null}</L10nValue>
    <L10nValue>{command.uncertain ? <Button type="button" variant="secondary" onClick={onRetry}><L10n>{"Gửi lại cùng nội dung"}</L10n></Button> : null}</L10nValue>
    <L10nValue>{retrying && command.error && !command.uncertain ? <p className="text-sm text-danger"><L10n>{"Lần gửi lại chưa được chấp nhận. Kiểm tra trạng thái trước khi soạn lệnh khác."}</L10n></p> : null}</L10nValue>
  </div>;
}


