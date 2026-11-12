"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import { useEffect, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import type { Role } from "@/lib/roles";
import { useCommand } from "@/lib/use-command";
import { appointmentApi } from "../api";
import { VitalSignHistory } from "./VitalSignHistory";
import type { AppointmentDTO, RegistrationDTO } from "../types";

interface OutpatientExamActionsProps {
  appointment: AppointmentDTO;
  role: Role | null;
  onUpdated: (appointment: AppointmentDTO) => void;
  onRefresh: () => void;
  clinicIntakeEnabled?: boolean;
}

export function OutpatientExamActions(props: OutpatientExamActionsProps) {
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  const sessionKey = token?.trim() ? token : null;

  return <OutpatientExamActionsForSession
    key={sessionKey ?? "anonymous"}
    {...props}
    role={sessionKey ? props.role : null}
  />;
}

function OutpatientExamActionsForSession({ appointment, role, onUpdated, onRefresh, clinicIntakeEnabled = false }: OutpatientExamActionsProps) {
  const command = useCommand();
  const [confirm, setConfirm] = useState<"check-in" | "start" | "cancel" | null>(null);
  const canCheckIn = (role === "ADMIN" || role === "NURSE")
    && appointment.status === "PENDING" && appointment.careContractVersion === 0;
  const [scheduledRead, setScheduledRead] = useState<{ id: string; item: RegistrationDTO | null; error: boolean } | null>(null);
  useEffect(() => {
    let active = true;
    if (appointment.careContractVersion >= 2 && role === "DOCTOR" && appointment.status === "READY_FOR_EXAM") {
      appointmentApi.registration(appointment.appointmentId).then(item => {
        if (active) setScheduledRead({ id: appointment.appointmentId, item: item.registrationId === appointment.appointmentId ? item : null, error: item.registrationId !== appointment.appointmentId });
      }).catch(() => { if (active) setScheduledRead({ id: appointment.appointmentId, item: null, error: true }); });
    }
    return () => { active = false; };
  }, [appointment, role]);
  const scheduled = scheduledRead?.id === appointment.appointmentId ? scheduledRead : null;
  const canStartScheduled = role === "DOCTOR" && appointment.careContractVersion >= 2
    && appointment.status === "READY_FOR_EXAM" && scheduled?.item?.readyForExam === true;
  const canStart = canStartScheduled || (role === "ADMIN" || role === "DOCTOR")
    && appointment.status === "READY_FOR_EXAM" && appointment.careContractVersion === 1
    && Boolean(appointment.examClearanceId);
  const missingRoomNumber = clinicIntakeEnabled && canStartScheduled && (scheduled?.item?.roomQueueNumber ?? null) === null;
  const showVitals = clinicIntakeEnabled && role === "DOCTOR" && appointment.careContractVersion >= 2
    && (appointment.status === "READY_FOR_EXAM" || appointment.status === "IN_EXAM");
  const canEditOrCancel = (role === "ADMIN" || role === "DOCTOR" || role === "NURSE")
    && appointment.status === "PENDING";

  function submit() {
    if (command.uncertain || !confirm) return;
    if ((confirm === "check-in" && !canCheckIn) || (confirm === "start" && (!canStart || missingRoomNumber))) return;
    if (confirm === "cancel" && !canEditOrCancel) return;
    void command.run(
      () => confirm === "check-in" ? appointmentApi.checkIn(appointment.appointmentId)
        : confirm === "cancel" ? appointmentApi.changeStatus(appointment.appointmentId, { status: "CANCELLED" })
          : appointmentApi.startExam(appointment.appointmentId),
      (updated) => { setConfirm(null); onUpdated(updated); },
    );
  }

  return (
    <section className="space-y-4 rounded-xl border border-border bg-surface p-6">
      <h2 className="text-lg font-semibold"><L10n>{"Khám ngoại trú"}</L10n></h2>
      <L10nValue>{appointment.status === "AWAITING_PAYMENT" ? <p role="status" className="text-sm text-warning"><L10n>{"Đang chờ xác nhận thanh toán phí khám. Chỉ bắt đầu khi hệ thống ghi nhận đủ điều kiện."}</L10n></p> : null}</L10nValue>
      <L10nValue>{appointment.status === "IN_EXAM" ? <p role="status" className="text-sm text-success"><L10n>{"Đã bắt đầu khám. Tiếp tục khám trong hồ sơ của lượt này."}</L10n></p> : null}</L10nValue>
      <L10nValue>{appointment.careContractVersion >= 2 && appointment.status === "READY_FOR_EXAM" && role === "DOCTOR" && !canStartScheduled ? <p role="status" className="text-sm text-muted-foreground"><L10nValue>{!scheduled ? "Đang kiểm tra điều kiện khám…" : scheduled.error ? "Chưa kiểm tra được điều kiện khám. Chọn Cập nhật trạng thái để thử lại." : !scheduled.item?.checkedInAt ? "Chờ bệnh nhân đến quầy tiếp nhận để ghi nhận có mặt." : "Chưa đủ điều kiện bắt đầu. Kiểm tra giờ khám và trạng thái phiếu, sau đó cập nhật lại."}</L10nValue></p> : null}</L10nValue>
      <L10nValue>{missingRoomNumber ? <p role="status" className="text-sm text-warning"><L10n>{"Bệnh nhân chưa có số phòng. Điều dưỡng khoa cần cấp số phòng trước khi bắt đầu khám."}</L10n></p> : null}</L10nValue>
      <L10nValue>{showVitals ? <VitalSignHistory registrationId={appointment.appointmentId} /> : null}</L10nValue>
      <CommandFeedback error={command.error} uncertain={command.uncertain} />
      <div className="flex flex-wrap gap-3">
        <L10nValue>{canCheckIn ? <Button type="button" variant="primary" disabled={command.busy || command.uncertain} onClick={() => setConfirm("check-in")}><L10n>{"Tiếp nhận bệnh nhân"}</L10n></Button> : null}</L10nValue>
        <L10nValue>{canStart ? <Button type="button" variant="primary" disabled={command.busy || command.uncertain || missingRoomNumber} onClick={() => setConfirm("start")}><L10n>{"Bắt đầu khám"}</L10n></Button> : null}</L10nValue>
        <L10nValue>{canEditOrCancel && !command.busy && !command.uncertain ? <Link href={`/appointments/${appointment.appointmentId}/edit`} className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted"><L10n>{"Chỉnh sửa lịch hẹn"}</L10n></Link> : null}</L10nValue>
        <L10nValue>{canEditOrCancel ? <Button type="button" variant="danger" disabled={command.busy || command.uncertain} onClick={() => setConfirm("cancel")}><L10n>{"Hủy lịch hẹn"}</L10n></Button> : null}</L10nValue>
        <Button type="button" disabled={command.busy} onClick={onRefresh}><L10n>{"Cập nhật trạng thái"}</L10n></Button>
      </div>
      <L10nValue>{confirm ? <div className="space-y-3 rounded-lg border border-border p-4">
        <p className="text-sm"><L10nValue>{confirm === "check-in" ? "Xác nhận tiếp nhận và mở khoản phí khám cho lịch hẹn này?" : confirm === "cancel" ? "Xác nhận hủy lịch hẹn này?" : "Xác nhận bắt đầu khám và tạo hồ sơ khám?"}</L10nValue></p>
        <div className="flex flex-wrap gap-2"><Button type="button" variant="primary" loading={command.busy} disabled={command.uncertain} onClick={submit}><L10n>{"Xác nhận"}</L10n></Button><Button type="button" disabled={command.busy} onClick={() => setConfirm(null)}><L10n>{"Quay lại"}</L10n></Button></div>
      </div> : null}</L10nValue>
    </section>
  );
}
