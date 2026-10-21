"use client";

import { useEffect, useId, useRef } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import type { ReportRequestState } from "../useReportRequest";

interface ReportRequestFeedbackProps {
  state: ReportRequestState<unknown>;
  idle: string;
  loading: string;
  onRetry: () => void;
}

export function ReportRequestFeedback({ state, idle, loading, onRetry }: ReportRequestFeedbackProps) {
  const feedbackRef = useRef<HTMLDivElement>(null);
  const descriptionId = useId();
  useEffect(() => {
    if (state.status !== "error") return;
    const feedback = feedbackRef.current;
    const focused = document.activeElement;
    // A read may settle while the user is already editing a new scope or working elsewhere.
    if (focused instanceof HTMLElement && focused !== document.body
      && (!feedback?.closest("section")?.contains(focused)
        || focused.matches("input, select, textarea, [contenteditable=true]"))) return;
    feedback?.focus();
  }, [state.status]);

  if (state.status === "idle") return <AsyncState kind="idle" message={idle} />;
  if (state.status === "loading") return <AsyncState kind="loading" message={loading} />;
  if (state.status !== "error") return null;
  return (
    <div ref={feedbackRef} role="group" aria-label="Lỗi tải báo cáo" aria-describedby={descriptionId}
      tabIndex={-1} className="rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger">
      <div id={descriptionId}>
        {state.forbidden ? (
          <div role="alert" className="mt-5 rounded-xl border border-danger/40 bg-surface p-4 text-sm">
            <p className="text-danger">{state.message}</p>
            {state.correlationId && <p className="mt-1 break-all text-xs text-muted-foreground">Mã theo dõi: {state.correlationId}</p>}
          </div>
        ) : <AsyncState kind="error" message={state.message} correlationId={state.correlationId} onRetry={onRetry} />}
        {state.code && <p className="mt-2 break-all text-xs text-muted-foreground">Mã lỗi: {state.code}</p>}
      </div>
    </div>
  );
}
