"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { ApiRequestError } from "@/lib/api";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { ReferenceText } from "@/components/references/ReferenceText";
import { queueNumberLabel, roomNumberText, registrationStatusLabels } from "../presentation";
import { appointmentApi } from "../api";
import type { RegistrationDTO } from "../types";

interface WorkspaceError { message: string; correlationId: string | null }

function errorFrom(cause: unknown, fallback: string): WorkspaceError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

export function PatientRegistrationWorkspace({ enabled, clinicIntakeEnabled = false }: { enabled: boolean; clinicIntakeEnabled?: boolean }) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <PatientRegistrationWorkspaceContent key={session ?? "anonymous"} enabled={enabled} clinicIntakeEnabled={clinicIntakeEnabled} />;
}

function PatientRegistrationWorkspaceContent({ enabled, clinicIntakeEnabled }: { enabled: boolean; clinicIntakeEnabled: boolean }) {
  const { localize } = useLocale();
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

  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground"><L10n>{"Tra cứu phiếu lịch khám chưa được mở."}</L10n></p>;

  return <div className="space-y-6">
    <section className="rounded-xl border border-border bg-surface p-5">
      <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-lg font-semibold"><L10n>{"Phiếu đăng ký của tôi"}</L10n></h2><p className="mt-1 text-sm text-muted-foreground"><L10n>{"Theo dõi lịch hẹn, thanh toán và hướng dẫn tái khám của bạn."}</L10n></p></div><Button type="button" loading={loading} onClick={() => void load(0)}><L10n>{"Tải phiếu của tôi"}</L10n></Button></div>
      <L10nValue>{error ? <AsyncState kind="error" message={localize(error.message)} correlationId={error.correlationId} onRetry={() => void load()} /> : null}</L10nValue>
      <L10nValue>{registrations ? registrations.length ? <ul className="mt-4 divide-y divide-border"><L10nValue>{registrations.map(item => <li key={item.registrationId} className="space-y-3 py-4">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="text-base font-semibold"><L10nValue>{item.visitType === "FOLLOW_UP" ? "Tái khám" : "Khám mới"}</L10nValue> · <L10nValue>{formatLocalDate(item.sessionDate)}</L10nValue></h3><p className="mt-1 text-sm"><L10nValue>{formatLocalDate(item.sessionDate)}</L10nValue> · {item.startTime}–{item.endTime}</p></div><span className="rounded-full border border-border px-3 py-1 text-xs font-medium"><L10nValue>{registrationStatusLabels[item.status]}</L10nValue></span></div>
        <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div><dt className="text-muted-foreground">Khoa</dt><dd><ReferenceText kind="department" value={item.departmentId} fallback="Khoa khám" /></dd></div>
          <div><dt className="text-muted-foreground"><L10n>{"Bác sĩ"}</L10n></dt><dd><ReferenceText kind="doctor" value={item.doctorId} fallback="Bác sĩ phụ trách" /></dd></div>
          <div><dt className="text-muted-foreground"><L10n>{"Phòng khám"}</L10n></dt><dd><ReferenceText kind="room" value={item.roomId} fallback="Phòng khám" /></dd></div>
          <div><dt className="text-muted-foreground"><L10n>{"Hình thức"}</L10n></dt><dd><L10nValue>{item.receptionMode === "AT_COUNTER" ? "Tiếp nhận tại quầy" : "Đăng ký trước"}</L10nValue></dd></div>
          <L10nValue>{item.status === "AWAITING_PAYMENT" ? <div><dt className="text-muted-foreground"><L10n>{"Hạn thanh toán giữ chỗ"}</L10n></dt><dd><L10nValue>{formatInstant(item.reservationExpiresAt)}</L10nValue></dd></div> : null}</L10nValue>
          <div><dt className="text-muted-foreground"><L10nValue>{queueNumberLabel(clinicIntakeEnabled)}</L10nValue></dt><dd><L10nValue>{item.queueNumber !== null ? item.queueNumber : "Chưa cấp số trước khi xác nhận"}</L10nValue></dd></div>
          <L10nValue>{clinicIntakeEnabled && roomNumberText(item.roomQueueNumber) ? <div><dt className="text-muted-foreground"><L10n>{"Số phòng"}</L10n></dt><dd>{item.roomQueueNumber}</dd></div> : null}</L10nValue>
        </dl>
        <L10nValue>{item.status === "CONFIRMED" ? <p role="status" className="rounded-lg border border-info/30 bg-info/5 p-3 text-sm"><L10nValue>{item.checkedInAt ? "Đã ghi nhận bạn có mặt. Vui lòng chờ gọi tên theo hướng dẫn tại phòng khám." : "Lịch khám đã xác nhận. Vui lòng đến quầy tiếp nhận vào ngày hẹn để ghi nhận có mặt trước khi khám."}</L10nValue></p> : null}</L10nValue>
        <L10nValue>{item.followUpRecommendation ? <section aria-label={localize("Đề xuất tái khám")} className="space-y-2 rounded-lg border border-info/30 bg-info/5 p-4">
          <div><h4 className="font-semibold"><L10n>{"Đề xuất tái khám"}</L10n></h4><p className="mt-1 text-sm"><L10n>{"Khoảng ngày khuyến nghị: "}</L10n><L10nValue>{formatLocalDate(item.followUpRecommendation.recommendedFrom)}</L10nValue> – <L10nValue>{formatLocalDate(item.followUpRecommendation.recommendedThrough)}</L10nValue></p></div>
          <div><h5 className="text-sm font-medium"><L10n>{"Lý do"}</L10n></h5><p className="mt-1 whitespace-pre-wrap text-sm">{item.followUpRecommendation.reason}</p></div>
          <L10nValue>{item.followUpRecommendation.preparationInstructions ? <div><h5 className="text-sm font-medium"><L10n>{"Hướng dẫn chuẩn bị"}</L10n></h5><p className="mt-1 whitespace-pre-wrap text-sm">{item.followUpRecommendation.preparationInstructions}</p></div> : null}</L10nValue>
        </section> : null}</L10nValue>
        <L10nValue>{item.status === "AWAITING_PAYMENT" ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm"><L10n>{"Phiếu đang chờ thanh toán và xác nhận. Lịch hẹn chưa được xác nhận."}</L10n></p> : null}</L10nValue>
      </li>)}</L10nValue></ul> : <p role="status" className="mt-4 text-sm text-muted-foreground"><L10n>{"Bạn chưa có phiếu đăng ký lịch khám."}</L10n></p> : null}</L10nValue>
      <L10nValue>{registrations && totalPages > 1 ? <nav aria-label={localize("Trang phiếu của tôi")} className="mt-4 flex items-center justify-between border-t border-border pt-4"><Button type="button" disabled={page === 0 || loading} onClick={() => void load(page - 1)}><L10n>{"Trang trước"}</L10n></Button><span className="text-sm text-muted-foreground">Trang <L10nValue>{page + 1}</L10nValue> / <L10nValue>{totalPages}</L10nValue></span><Button type="button" disabled={page + 1 >= totalPages || loading} onClick={() => void load(page + 1)}>Trang sau</Button></nav> : null}</L10nValue>
    </section>
  </div>;
}
