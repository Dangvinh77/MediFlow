"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useId, useRef, type ReactNode } from "react";
import Link from "next/link";
import { getRole } from "@/lib/auth";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import type { ReportRequestState } from "../useReportRequest";

interface ReportRequestFeedbackProps {
  state: ReportRequestState<unknown>;
  idle: string;
  loading: string;
  onRetry: () => void;
  submittedScope?: ReactNode;
}

export function ReportRequestFeedback({ state, idle, loading, onRetry, submittedScope }: ReportRequestFeedbackProps) {
  const { localize } = useLocale();
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

  if (state.status === "idle") return <AsyncState kind="idle" message={localize(idle)} />;
  if (state.status === "loading") return <AsyncState kind="loading" message={localize(loading)} />;
  if (state.status !== "error") return null;
  const unavailable = !state.forbidden && state.code === "REPORT_NOT_FOUND";
  return (
    <div ref={feedbackRef} role="group" aria-label={localize(unavailable ? "Báo cáo chưa sẵn sàng" : "Lỗi tải báo cáo")} aria-describedby={descriptionId}
      tabIndex={-1} className={`rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 ${unavailable ? "focus-visible:outline-primary" : "focus-visible:outline-danger"}`}>
      <div id={descriptionId}>
        <L10nValue>{unavailable ? <div className="mt-5 space-y-3 rounded-xl border border-border bg-surface-muted p-4 text-sm">
          <h2 className="font-semibold"><L10n>{"Báo cáo chưa sẵn sàng"}</L10n></h2>
          <L10nValue>{submittedScope && <div><L10n>{"Phạm vi đã yêu cầu: "}</L10n><L10nValue>{submittedScope}</L10nValue></div>}</L10nValue>
          <p><L10n>{"Chưa có báo cáo khả dụng cho phạm vi đã yêu cầu. Nguồn báo cáo có thể chưa được bật hoặc chưa có dữ liệu đã đối soát; trạng thái này không có nghĩa là không phát sinh nghiệp vụ."}</L10n></p>
          <p><L10n>{"Báo cáo ngoại trú theo ngày cần kỳ đã kết thúc theo giờ Việt Nam và đủ nguồn được quản trị viên đối soát, chấp nhận. Tải lại chỉ kiểm tra trạng thái, không tạo dữ liệu hay phê duyệt phạm vi."}</L10n></p>
          <p className="text-muted-foreground"><L10n>{"Đối chiếu tại phân hệ nghiệp vụ hoặc liên hệ quản trị viên kiểm tra nguồn và phạm vi đối soát trước khi sử dụng số liệu."}</L10n></p>
          <L10nValue>{state.correlationId && <p className="break-all text-xs text-muted-foreground"><L10n>{"Mã theo dõi: "}</L10n>{state.correlationId}</p>}</L10nValue>
          <div className="flex flex-wrap items-center gap-4">
            <Button type="button" onClick={onRetry}><L10n>{"Thử lại"}</L10n></Button>
            <L10nValue>{(getRole() === "ADMIN" || getRole() === "DOCTOR") && <Link href="/appointments" className="inline-flex min-h-11 items-center font-semibold text-primary underline focus-visible:outline-2 focus-visible:outline-primary"><L10n>{"Đối chiếu lịch hẹn"}</L10n></Link>}</L10nValue>
          </div>
        </div> : state.forbidden ? (
          <div role="alert" className="mt-5 rounded-xl border border-danger/40 bg-surface p-4 text-sm">
            <p className="text-danger"><L10nValue>{state.message}</L10nValue></p>
            <L10nValue>{state.correlationId && <p className="mt-1 break-all text-xs text-muted-foreground"><L10n>{"Mã theo dõi: "}</L10n>{state.correlationId}</p>}</L10nValue>
          </div>
        ) : <AsyncState kind="error" message={localize(state.message)} correlationId={state.correlationId} onRetry={onRetry} />}</L10nValue>
        <L10nValue>{state.code && <p className="mt-2 break-all text-xs text-muted-foreground"><L10n>{"Mã lỗi: "}</L10n>{state.code}</p>}</L10nValue>
      </div>
    </div>
  );
}
