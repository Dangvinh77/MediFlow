"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { controlClassName } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import {
  getRole,
  subscribeToAuthChanges,
} from "@/lib/auth";
import type { Role } from "@/lib/roles";
import { getPharmacyCapabilities } from "../../permissions";
import { pharmacyApi } from "../../api";
import { hasUncertainMutationStatus } from "../../mutationOutcome";
import type { OutboxReplayResult } from "../../types";
import { isUuid } from "../../utils";
import { PharmacySessionBoundary, usePharmacyRequestScope } from "../PharmacySessionBoundary";

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
      return "Bạn không có quyền khôi phục đồng bộ dữ liệu.";
    }

    if (cause.code === "OUTBOX_EVENT_NOT_FOUND" || cause.status === 404) {
      return "Không tìm thấy yêu cầu đồng bộ với mã đã nhập.";
    }

    return hasUncertainMutationStatus(cause)
      ? `${cause.message} Chưa xác định kết quả replay; hãy kiểm tra trạng thái event trước khi thử lại.`
      : cause.message;
  }

  const message = cause instanceof Error ? cause.message : "Chưa thể khôi phục đồng bộ dữ liệu.";
  return `${message} Chưa xác định kết quả replay; hãy kiểm tra trạng thái event trước khi thử lại.`;
}

function correlationIdOf(cause: unknown): string | null {
  return cause instanceof ApiRequestError ? cause.correlationId : null;
}

/** Replays one known Pharmacy outbox event; it intentionally never lists events. */
export function OutboxReplayForm() {
  return <PharmacySessionBoundary><OutboxReplayFormContent /></PharmacySessionBoundary>;
}

function OutboxReplayFormContent() {
  const isCurrent = usePharmacyRequestScope("canReplayOutbox");
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
  const [forbidden, setForbidden] = useState(false);
  const retryRef = useRef<HTMLButtonElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);
  const previousKind = useRef<ReplayState["kind"]>("idle");

  const confirming = state.kind === "confirming";
  const submitting = state.kind === "submitting";
  const showingConfirmation = confirming || submitting;

  useEffect(() => {
    // The field is disabled during confirmation; focus only after React enables it again.
    if (state.kind === "idle" && (previousKind.current === "confirming" || previousKind.current === "success")) {
      inputRef.current?.focus();
    }
    previousKind.current = state.kind;
  }, [state.kind]);

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
    if (submittingRef.current || forbidden || !isCurrent()) return;

    const normalizedId = eventId.trim();
    if (!isUuid(normalizedId)) {
      setFieldError("Mã yêu cầu không hợp lệ. Vui lòng kiểm tra mã do bộ phận kỹ thuật cung cấp.");
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
    if (state.kind !== "confirming" || submittingRef.current || forbidden || !isCurrent()) return;

    const normalizedId = state.eventId;
    submittingRef.current = true;
    setState({ kind: "submitting", eventId: normalizedId });

    try {
      const result = await pharmacyApi.replayOutbox(normalizedId);
      if (!isCurrent()) return;
      setState({ kind: "success", result });
    } catch (cause: unknown) {
      if (!isCurrent()) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      if (cause instanceof ApiRequestError && cause.status === 403) setForbidden(true);
      setState({
        kind: "error",
        eventId: normalizedId,
        message: errorMessage(cause),
        correlationId: correlationIdOf(cause),
        focus:
          cause instanceof ApiRequestError && cause.status === 404
            ? "input"
            : !(cause instanceof ApiRequestError) || cause.status === 403 || hasUncertainMutationStatus(cause)
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
        Tài khoản của bạn không có quyền khôi phục đồng bộ dữ liệu.
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
          {state.result.replayed ? "Đã gửi yêu cầu đồng bộ lại" : "Chưa gửi được yêu cầu đồng bộ lại"}
        </h2>
        <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-muted-foreground">Mã yêu cầu</dt>
            <dd className="mt-1 break-all font-mono text-xs">{state.result.eventId}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Đã gửi lại</dt>
            <dd className="mt-1 font-semibold">{state.result.replayed ? "Có" : "Không"}</dd>
          </div>
        </dl>
        <p className="mt-4 text-sm text-muted-foreground">
          Yêu cầu đã được gửi lại để xử lý. Kết quả nghiệp vụ có thể chưa cập nhật ngay;
          liên hệ bộ phận kỹ thuật để kiểm tra nếu dữ liệu vẫn chưa đồng bộ.
        </p>
        <button
          type="button"
          onClick={startAnotherReplay}
          className="mt-5 min-h-11 rounded-lg border border-border bg-surface px-4 py-2 font-medium hover:bg-surface-muted"
        >
          Khôi phục yêu cầu khác
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
      <h2 className="text-base font-semibold">Khôi phục một yêu cầu đồng bộ</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Chỉ dành cho quản trị viên khi xử lý sự cố đồng bộ. Nhập mã yêu cầu do bộ phận
        kỹ thuật cung cấp; các thao tác kê đơn và xuất thuốc nằm trong mục Đơn thuốc.
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
            Mã yêu cầu
          </label>
          <input
            ref={inputRef}
            id="outbox-event-id"
            value={eventId}
            disabled={submitting || confirming || forbidden}
            onChange={(event) => {
              setEventId(event.target.value);
              clearFeedback();
            }}
            aria-invalid={fieldError ? true : undefined}
            aria-describedby={fieldError ? "outbox-event-id-error" : undefined}
            className={`${controlClassName} font-mono ${fieldError ? "border-danger" : ""}`}
            placeholder="Dán mã yêu cầu cần khôi phục"
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
            className="min-h-11 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Xem xác nhận
          </button>
        )}
      </form>

      {showingConfirmation && (
        <section
          className="mt-5 rounded-lg border border-warning/40 bg-warning/10 p-4"
          aria-labelledby="outbox-replay-confirm-title"
          aria-describedby="outbox-replay-confirm-description"
        >
          <h3 id="outbox-replay-confirm-title" className="font-semibold">
            Xác nhận khôi phục đồng bộ
          </h3>
          <p id="outbox-replay-confirm-description" className="mt-2 text-sm">
            Hệ thống sẽ gửi lại yêu cầu <code className="break-all">{currentEventId}</code> để đồng bộ.
            Dữ liệu gốc được giữ nguyên. Việc gửi lại chưa đồng nghĩa nghiệp vụ đã hoàn tất.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={cancelConfirmation}
              disabled={submitting}
              className="min-h-11 rounded-lg border border-border bg-surface px-4 py-2 font-medium hover:bg-surface-muted disabled:opacity-50"
            >
              Quay lại
            </button>
            <button
              ref={confirmRef}
              type="button"
              onClick={() => void confirmReplay()}
              disabled={submitting}
              className="min-h-11 rounded-lg bg-warning px-4 py-2 font-medium text-warning-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting ? "Đang gửi yêu cầu…" : "Xác nhận khôi phục"}
            </button>
          </div>
        </section>
      )}
    </section>
  );
}
