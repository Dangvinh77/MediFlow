"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { ApiRequestError } from "@/lib/api";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { ReferenceText } from "@/components/references/ReferenceText";
import { registrationStatusLabels } from "../presentation";
import { appointmentApi } from "../api";
import type { RegistrationDTO } from "../types";

interface WorkspaceError { message: string; correlationId: string | null }

function errorFrom(cause: unknown, fallback: string): WorkspaceError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

export function PatientRegistrationWorkspace({ enabled }: { enabled: boolean }) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <PatientRegistrationWorkspaceContent key={session ?? "anonymous"} enabled={enabled} />;
}

function PatientRegistrationWorkspaceContent({ enabled }: { enabled: boolean }) {
  const [page, setPage] = useState(0);
  const [registrations, setRegistrations] = useState<RegistrationDTO[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<WorkspaceError | null>(null);

  const readGeneration = useRef(0);
  useEffect(() => {
    if (!enabled) return;
    const reads = readGeneration;
    const generation = ++reads.current;
    appointmentApi.myRegistrations({ page: 0, size: 10 }).then(result => {
      if (generation !== readGeneration.current) return;
      setPage(result.number); setTotalPages(result.totalPages); setRegistrations(result.content);
    }).catch(cause => {
      if (generation === readGeneration.current) setError(errorFrom(cause, "Không thể tải dữ liệu của bạn."));
    }).finally(() => { if (generation === readGeneration.current) setLoading(false); });
    return () => { reads.current++; };
  }, [enabled]);

  async function load(nextPage = page) {
    if (!enabled || loading) return;
    const generation = ++readGeneration.current;
    setLoading(true); setError(null);
    try {
      const result = await appointmentApi.myRegistrations({ page: nextPage, size: 10 });
      if (generation !== readGeneration.current) return;
      setPage(result.number); setTotalPages(result.totalPages); setRegistrations(result.content);
    } catch (cause) {
      if (generation === readGeneration.current) setError(errorFrom(cause, "Không thể tải phiếu khám của bạn."));
    } finally {
      if (generation === readGeneration.current) setLoading(false);
    }
  }

  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground">Tra cứu phiếu lịch khám chưa được mở.</p>;

  return <div className="space-y-6">
    <section className="rounded-xl border border-border bg-surface p-5">
      <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-lg font-semibold">Phiếu đăng ký của tôi</h2><p className="mt-1 text-sm text-muted-foreground">Theo dõi lịch hẹn, thanh toán và hướng dẫn tái khám của bạn.</p></div><Button type="button" loading={loading} onClick={() => void load(0)}>Tải phiếu của tôi</Button></div>
      {error ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => void load()} /> : null}
      {registrations ? registrations.length ? <ul className="mt-4 divide-y divide-border">{registrations.map(item => <li key={item.registrationId} className="space-y-3 py-4">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="text-base font-semibold">{item.visitType === "FOLLOW_UP" ? "Tái khám" : "Khám mới"} · {formatLocalDate(item.sessionDate)}</h3><p className="mt-1 text-sm">{formatLocalDate(item.sessionDate)} · {item.startTime}–{item.endTime}</p></div><span className="rounded-full border border-border px-3 py-1 text-xs font-medium">{registrationStatusLabels[item.status]}</span></div>
        <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div><dt className="text-muted-foreground">Khoa</dt><dd><ReferenceText kind="department" value={item.departmentId} fallback="Khoa khám" /></dd></div>
          <div><dt className="text-muted-foreground">Bác sĩ</dt><dd><ReferenceText kind="doctor" value={item.doctorId} fallback="Bác sĩ phụ trách" /></dd></div>
          <div><dt className="text-muted-foreground">Phòng khám</dt><dd><ReferenceText kind="room" value={item.roomId} fallback="Phòng khám" /></dd></div>
          <div><dt className="text-muted-foreground">Hình thức</dt><dd>{item.receptionMode === "AT_COUNTER" ? "Tiếp nhận tại quầy" : "Đăng ký trước"}</dd></div>
          {item.status === "AWAITING_PAYMENT" ? <div><dt className="text-muted-foreground">Hạn thanh toán giữ chỗ</dt><dd>{formatInstant(item.reservationExpiresAt)}</dd></div> : null}
          <div><dt className="text-muted-foreground">Số thứ tự</dt><dd>{item.queueNumber !== null ? item.queueNumber : "Chưa cấp số trước khi xác nhận"}</dd></div>
        </dl>
        {item.status === "CONFIRMED" ? <p role="status" className="rounded-lg border border-info/30 bg-info/5 p-3 text-sm">{item.checkedInAt ? "Đã ghi nhận bạn có mặt. Vui lòng chờ gọi tên theo hướng dẫn tại phòng khám." : "Lịch khám đã xác nhận. Vui lòng đến quầy tiếp nhận vào ngày hẹn để ghi nhận có mặt trước khi khám."}</p> : null}
        {item.followUpRecommendation ? <section aria-label="Đề xuất tái khám" className="space-y-2 rounded-lg border border-info/30 bg-info/5 p-4">
          <div><h4 className="font-semibold">Đề xuất tái khám</h4><p className="mt-1 text-sm">Khoảng ngày khuyến nghị: {formatLocalDate(item.followUpRecommendation.recommendedFrom)} – {formatLocalDate(item.followUpRecommendation.recommendedThrough)}</p></div>
          <div><h5 className="text-sm font-medium">Lý do</h5><p className="mt-1 whitespace-pre-wrap text-sm">{item.followUpRecommendation.reason}</p></div>
          {item.followUpRecommendation.preparationInstructions ? <div><h5 className="text-sm font-medium">Hướng dẫn chuẩn bị</h5><p className="mt-1 whitespace-pre-wrap text-sm">{item.followUpRecommendation.preparationInstructions}</p></div> : null}
        </section> : null}
        {item.status === "AWAITING_PAYMENT" ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">Phiếu đang chờ thanh toán và xác nhận. Lịch hẹn chưa được xác nhận.</p> : null}
      </li>)}</ul> : <p role="status" className="mt-4 text-sm text-muted-foreground">Bạn chưa có phiếu đăng ký lịch khám.</p> : null}
      {registrations && totalPages > 1 ? <nav aria-label="Trang phiếu của tôi" className="mt-4 flex items-center justify-between border-t border-border pt-4"><Button type="button" disabled={page === 0 || loading} onClick={() => void load(page - 1)}>Trang trước</Button><span className="text-sm text-muted-foreground">Trang {page + 1} / {totalPages}</span><Button type="button" disabled={page + 1 >= totalPages || loading} onClick={() => void load(page + 1)}>Trang sau</Button></nav> : null}
    </section>
  </div>;
}
