"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { notificationApi } from "../api";
import { channelLabels, statusPresentation } from "../presentation";
import type { NotificationDTO } from "../types";
import { NotificationAccess, NotificationDenied, useNotificationRequestScope } from "./NotificationAccess";

interface Props { notificationId: string; notice?: "sent"; backHref?: string }

export function NotificationDetail(props: Props) {
  return <NotificationAccess permission="detail"><DetailWorkspace key={props.notificationId} {...props} /></NotificationAccess>;
}

function DetailWorkspace(props: Props) {
  const [attempt, setAttempt] = useState(0);
  if (!isUuid(props.notificationId)) return <p role="alert" className="mt-6 text-danger">Mã thông báo không hợp lệ.</p>;
  return <DetailRequest key={attempt} {...props} focusResult={attempt > 0} onRetry={() => setAttempt(value => value + 1)} />;
}

function DetailRequest({ notificationId, notice, backHref, onRetry, focusResult }: Props & { onRetry: () => void; focusResult: boolean }) {
  const router = useRouter();
  const isCurrent = useNotificationRequestScope("detail");
  const [notification, setNotification] = useState<NotificationDTO | null>(null);
  const [error, setError] = useState<{ message: string; correlationId: string | null; forbidden: boolean } | null>(null);
  const feedbackRef = useRef<HTMLDivElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const descriptionId = useId();
  useEffect(() => {
    // A retry removes its trigger. Restore focus only if the user has not moved elsewhere.
    if (document.activeElement !== document.body) return;
    if (error) feedbackRef.current?.focus();
    else if (notification && focusResult) headingRef.current?.focus();
  }, [error, notification, focusResult]);
  useEffect(() => {
    let active = true;
    notificationApi.getById(notificationId).then(value => {
      if (active && isCurrent()) setNotification(value);
    }).catch((cause: unknown) => {
      if (!active || !isCurrent()) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      setError({
        message: cause instanceof ApiRequestError && cause.status === 403 ? "Bạn không có quyền xem thông báo này."
          : cause instanceof ApiRequestError && cause.status === 404 ? "Không tìm thấy thông báo."
          : cause instanceof Error ? cause.message : "Không thể tải thông báo.",
        correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
        forbidden: cause instanceof ApiRequestError && cause.status === 403,
      });
    });
    return () => { active = false; };
  }, [notificationId, router, isCurrent]);
  if (error) return (
    <>
      <div ref={feedbackRef} role="group" aria-label="Lỗi tải thông báo" aria-describedby={descriptionId} tabIndex={-1}
        className="rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger">
        <div id={descriptionId}>
          {error.forbidden
            ? <NotificationDenied message={error.message} correlationId={error.correlationId} />
            : <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={onRetry} />}
        </div>
      </div>
      <Link href={backHref ?? "/notifications"} className="mt-4 inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-primary">Quay lại tra cứu</Link>
    </>
  );
  if (!notification) return <AsyncState kind="loading" message="Đang tải thông báo…" />;
  const status = statusPresentation[notification.status] ?? { label: "Không xác định", tone: "neutral" as const };
  return (
    <section className="mt-6 space-y-6">
      {notice === "sent" && <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">Đã tiếp nhận yêu cầu gửi thông báo.</p>}

      <section aria-label="Tổng quan" className="rounded-xl border border-border bg-surface p-6">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">Tổng quan</h2>
        <dl className="mt-3 grid gap-5 sm:grid-cols-2">
          <Info label="Bệnh nhân" value={notification.patientId} />
          <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Trạng thái</dt><dd className="mt-1"><StatusBadge tone={status.tone}>{status.label}</StatusBadge></dd></div>
          <Info label="Tạo lúc" value={formatInstant(notification.createdAt)} />
          <Info label="Gửi lúc" value={formatInstant(notification.sentAt)} />
        </dl>
      </section>

      <section aria-label="Tiêu đề và nội dung" className="space-y-4 rounded-xl border border-border bg-surface p-6">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Tiêu đề</p>
          <h2 ref={headingRef} tabIndex={-1} className="mt-1 rounded-sm break-words text-xl font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">{notification.title}</h2>
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Nội dung</p>
          <p className="mt-1 whitespace-pre-wrap break-words text-sm">{notification.content}</p>
        </div>
      </section>

      <section aria-label="Thông tin gửi" className="rounded-xl border border-border bg-surface p-6">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">Thông tin gửi</h2>
        <dl className="mt-3 grid gap-5 sm:grid-cols-2">
          <Info label="Kênh" value={channelLabels[notification.channel] ?? "Không xác định"} />
          <Info label="Lý do thất bại" value={notification.failureReason} />
        </dl>
      </section>

      <section aria-label="Thông tin kỹ thuật" className="rounded-xl border border-border bg-surface p-6">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">Thông tin kỹ thuật</h2>
        <p className="mt-2 break-all font-mono text-xs text-muted-foreground">{notification.notificationId}</p>
      </section>

      <div className="flex flex-wrap gap-3">
        <button type="button" onClick={onRetry} className="min-h-11 rounded-lg border border-control-border px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-primary">Tải lại thông báo</button>
        <Link href={backHref ?? "/notifications"} className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-primary">Quay lại tra cứu</Link>
      </div>
    </section>
  );
}
function Info({ label, value }: { label: string; value: string | null }) {
  return <div className="min-w-0"><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-all text-sm">{["Bệnh nhân", "Mã bệnh nhân", "Mã đợt điều trị"].includes(label) ? <ReferenceText kind={label.includes("bệnh nhân") || label === "Bệnh nhân" ? "patient" : undefined} value={value} fallback={label} /> : value ?? "—"}</dd></div>;
}
