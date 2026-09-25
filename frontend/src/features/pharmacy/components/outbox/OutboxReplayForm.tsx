"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import {
  getRole,
  subscribeToAuthChanges,
} from "@/lib/auth";
import type { Role } from "@/lib/roles";
import { getPharmacyCapabilities } from "../../permissions";
import { pharmacyApi } from "../../api";
import type { OutboxReplayResult } from "../../types";
import { isUuid } from "../../utils";

type ReplayState =
  | { kind: "idle" }
  | { kind: "confirming"; eventId: string }
  | { kind: "submitting"; eventId: string }
  | { kind: "success"; result: OutboxReplayResult }
  | {
      kind: "error";
      eventId: string;
      message: string;
      correlationId: string | null;
      focus: "input" | "submit" | "alert";
      retryable: boolean;
    };

const getServerRole = (): Role | null => null;

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) {
      return "Bạn không có quyền replay outbox event này.";
    }

    if (cause.code === "OUTBOX_EVENT_NOT_FOUND" || cause.status === 404) {
      return "Không tìm thấy outbox event với mã đã nhập.";
    }

    return cause.message;
  }

  return cause instanceof Error
    ? cause.message
    : "Không thể replay outbox event.";
}

function correlationIdOf(cause: unknown): string | null {
  return cause instanceof ApiRequestError ? cause.correlationId : null;
}

/** Replays one known Pharmacy outbox event; it intentionally never lists events. */
export function OutboxReplayForm() {
  const router = useRouter();
  const role = useSyncExternalStore(
    subscribeToAuthChanges,
    getRole,
    getServerRole,
  );
  const capabilities = getPharmacyCapabilities(role);
  const inputRef = useRef<HTMLInputElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const submittingRef = useRef(false);
  const [eventId, setEventId] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [state, setState] = useState<ReplayState>({ kind: "idle" });
  const retryRef = useRef<HTMLButtonElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);

  const confirming = state.kind === "confirming";
  const submitting = state.kind === "submitting";
  const showingConfirmation = confirming || submitting;

  useEffect(() => {
    if (!confirming) return undefined;

    confirmRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !submittingRef.current) {
        event.preventDefault();
        setState({ kind: "idle" });
        inputRef.current?.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [confirming]);

  useEffect(() => {
    if (state.kind !== "error") return undefined;

    const focusTarget = state.focus === "input"
      ? inputRef
      : state.focus === "submit"
        ? retryRef
        : errorRef;
    const frame = window.requestAnimationFrame(() => focusTarget.current?.focus());

    return () => window.cancelAnimationFrame(frame);
  }, [state]);

  function clearFeedback() {
    setFieldError(null);
    if (state.kind !== "submitting") {
      setState({ kind: "idle" });
    }
  }

  function prepareConfirmation(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const normalizedId = eventId.trim();
    if (!isUuid(normalizedId)) {
      setFieldError("Event ID phải là UUID hợp lệ.");
      setState({ kind: "idle" });
      inputRef.current?.focus();
      return;
    }

    setFieldError(null);
    setState({ kind: "confirming", eventId: normalizedId });
  }

  function cancelConfirmation() {
    if (submittingRef.current) return;
    setState({ kind: "idle" });
    inputRef.current?.focus();
  }

  async function confirmReplay() {
    if (state.kind !== "confirming" || submittingRef.current) return;

    const normalizedId = state.eventId;
    submittingRef.current = true;
    setState({ kind: "submitting", eventId: normalizedId });

    try {
      const result = await pharmacyApi.replayOutbox(normalizedId);
      setState({ kind: "success", result });
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      setState({
        kind: "error",
        eventId: normalizedId,
        message: errorMessage(cause),
        correlationId: correlationIdOf(cause),
        focus:
          cause instanceof ApiRequestError && cause.status === 404
            ? "input"
            : cause instanceof ApiRequestError && cause.status === 403
              ? "alert"
              : "submit",
        retryable: !(cause instanceof ApiRequestError && cause.status === 403),
      });
    } finally {
      submittingRef.current = false;
    }
  }

  function startAnotherReplay() {
    setEventId("");
    setFieldError(null);
    setState({ kind: "idle" });
    inputRef.current?.focus();
  }

  if (role === null) {
    return (
      <p role="status" className="mt-6 text-sm text-muted-foreground">
        Đang kiểm tra quyền…
      </p>
    );
  }

  if (!capabilities.canReplayOutbox) {
    return (
      <p
        role="alert"
        className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"
      >
        Tài khoản của bạn không có quyền replay outbox event.
      </p>
    );
  }

  if (state.kind === "success") {
    return (
      <section
        className="mt-6 rounded-xl border border-success/40 bg-success/10 p-6"
        aria-labelledby="outbox-replay-success-title"
        aria-live="polite"
      >
        <h2 id="outbox-replay-success-title" className="font-semibold text-success">
          Đã đưa event trở lại hàng đợi replay
        </h2>
        <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-muted-foreground">Event ID</dt>
            <dd className="mt-1 break-all font-mono text-xs">{state.result.eventId}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Replayed</dt>
            <dd className="mt-1 font-semibold">{state.result.replayed ? "Có" : "Không"}</dd>
          </div>
        </dl>
        <p className="mt-4 text-sm text-muted-foreground">
          Replay chỉ đưa lại event bất biến vào hàng đợi. Việc consumer xử lý thành công cần được
          theo dõi ở hệ thống quan sát bên ngoài.
        </p>
        <button
          type="button"
          onClick={startAnotherReplay}
          className="mt-5 rounded-lg border border-border bg-surface px-4 py-2 font-medium hover:bg-surface-muted"
        >
          Replay event khác
        </button>
      </section>
    );
  }

  const error = state.kind === "error" ? state : null;
  const currentEventId =
    state.kind === "confirming" || state.kind === "submitting"
      ? state.eventId
      : error?.eventId ?? eventId.trim();

  return (
    <section className="mt-6 max-w-2xl rounded-xl border border-border bg-surface p-6">
      <h2 className="text-base font-semibold">Replay event đã biết</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Nhập đúng UUID lấy từ nguồn quan sát/quarantine bên ngoài. Màn hình này không liệt kê hoặc
        tìm kiếm outbox event.
      </p>

      {error && (
        <div
          ref={errorRef}
          role="alert"
          tabIndex={-1}
          className="mt-4 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger"
        >
          <p>{error.message}</p>
          {error.correlationId && (
            <p className="mt-1 text-xs text-muted-foreground">
              Mã theo dõi: <span className="font-mono">{error.correlationId}</span>
            </p>
          )}
        </div>
      )}

      <form onSubmit={prepareConfirmation} noValidate className="mt-5 space-y-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="outbox-event-id" className="text-sm font-medium">
            Event ID
          </label>
          <input
            ref={inputRef}
            id="outbox-event-id"
            value={eventId}
            disabled={submitting || confirming}
            onChange={(event) => {
              setEventId(event.target.value);
              clearFeedback();
            }}
            aria-invalid={fieldError ? true : undefined}
            aria-describedby={fieldError ? "outbox-event-id-error" : undefined}
            className={`w-full rounded-lg border bg-surface px-3 py-2 font-mono text-sm text-foreground placeholder:font-sans placeholder:text-muted-foreground ${fieldError ? "border-danger" : "border-border"}`}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
          />
          {fieldError && (
            <p id="outbox-event-id-error" className="text-xs text-danger">
              {fieldError}
            </p>
          )}
        </div>

        {!showingConfirmation && (
          <button
            ref={retryRef}
            type="submit"
            disabled={submitting || error?.retryable === false}
            className="rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Xem xác nhận
          </button>
        )}
      </form>

      {showingConfirmation && (
        <div
          className="mt-5 rounded-lg border border-warning/40 bg-warning/10 p-4"
          role="alertdialog"
          aria-labelledby="outbox-replay-confirm-title"
          aria-describedby="outbox-replay-confirm-description"
        >
          <h3 id="outbox-replay-confirm-title" className="font-semibold">
            Xác nhận replay event
          </h3>
          <p id="outbox-replay-confirm-description" className="mt-2 text-sm">
            Hệ thống sẽ đưa lại đúng event <code className="break-all">{currentEventId}</code> vào
            hàng đợi. Thao tác này không sửa payload và không đảm bảo consumer xử lý nghiệp vụ thành công.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={cancelConfirmation}
              disabled={submitting}
              className="rounded-lg border border-border bg-surface px-4 py-2 font-medium hover:bg-surface-muted disabled:opacity-50"
            >
              Quay lại
            </button>
            <button
              ref={confirmRef}
              type="button"
              onClick={() => void confirmReplay()}
              disabled={submitting}
              className="rounded-lg bg-warning px-4 py-2 font-medium text-warning-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting ? "Đang replay…" : "Xác nhận replay"}
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
