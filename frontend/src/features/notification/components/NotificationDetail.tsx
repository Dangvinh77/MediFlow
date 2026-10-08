"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { notificationApi } from "../api";
import type { NotificationDTO } from "../types";

export function NotificationDetail({ notificationId, notice }: { notificationId: string; notice?: "sent" }) {
  const router = useRouter(); const [notification, setNotification] = useState<NotificationDTO | null>(null); const [error, setError] = useState<string | null>(null);
  useEffect(() => { let mounted = true; notificationApi.getById(notificationId).then((value) => { if (mounted) setNotification(value); }).catch((cause: unknown) => { if (!mounted) return; if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể tải thông báo."); }); return () => { mounted = false; }; }, [notificationId, router]);
  if (error) return <AsyncState kind="error" message={error} onRetry={() => window.location.reload()} />; if (!notification) return <AsyncState kind="loading" message="Đang tải thông báo…" />;
  const tone = notification.status === "SENT" ? "success" : notification.status === "FAILED" ? "danger" : "warning";
  return <section className="mt-6 space-y-6">{notice === "sent" ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">Đã tiếp nhận yêu cầu gửi thông báo.</p> : null}<div className="flex items-start justify-between gap-4 rounded-xl border border-border bg-surface p-6"><div><p className="text-sm text-muted-foreground">{notification.channel}</p><h2 className="mt-1 text-xl font-semibold">{notification.title}</h2><p className="mt-2 break-all font-mono text-xs text-muted-foreground">{notification.notificationId}</p></div><StatusBadge tone={tone}>{notification.status}</StatusBadge></div><dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2"><Info label="Bệnh nhân" value={notification.patientId} /><Info label="Tạo lúc" value={formatInstant(notification.createdAt)} /><Info label="Gửi lúc" value={formatInstant(notification.sentAt)} /><Info label="Lý do thất bại" value={notification.failureReason} /></dl><section className="rounded-xl border border-border bg-surface p-6"><h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">Nội dung</h2><p className="mt-3 whitespace-pre-wrap text-sm">{notification.content}</p></section><p className="text-sm text-muted-foreground">Địa chỉ người nhận và số lần thử lại là dữ liệu riêng tư nội bộ, không thuộc response contract.</p><Link href="/notifications" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold">Quay lại tra cứu</Link></section>;
}
function Info({ label, value }: { label: string; value: string | null }) { return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-all text-sm">{value ?? "—"}</dd></div>; }
