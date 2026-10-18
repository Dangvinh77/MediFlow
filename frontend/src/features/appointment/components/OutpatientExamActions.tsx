"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import type { Role } from "@/lib/roles";
import { useCommand } from "@/lib/use-command";
import { appointmentApi } from "../api";
import type { AppointmentDTO } from "../types";

export function OutpatientExamActions({ appointment, role, onUpdated, onRefresh }: {
  appointment: AppointmentDTO;
  role: Role | null;
  onUpdated: (appointment: AppointmentDTO) => void;
  onRefresh: () => void;
}) {
  const command = useCommand();
  const [confirm, setConfirm] = useState<"check-in" | "start" | "cancel" | null>(null);
  const canCheckIn = (role === "ADMIN" || role === "NURSE")
    && appointment.status === "PENDING" && appointment.careContractVersion === 0;
  const canStart = (role === "ADMIN" || role === "DOCTOR")
    && appointment.status === "READY_FOR_EXAM" && appointment.careContractVersion === 1
    && Boolean(appointment.examClearanceId);
  const canEditOrCancel = (role === "ADMIN" || role === "DOCTOR" || role === "NURSE")
    && appointment.status === "PENDING";

  function submit() {
    if (command.uncertain || !confirm) return;
    if ((confirm === "check-in" && !canCheckIn) || (confirm === "start" && !canStart)) return;
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
      <h2 className="text-lg font-semibold">Khám ngoại trú</h2>
      {appointment.status === "AWAITING_PAYMENT" ? <p role="status" className="text-sm text-warning">Đang chờ xác nhận thanh toán phí khám. Chỉ bắt đầu khi hệ thống ghi nhận đủ điều kiện.</p> : null}
      {appointment.status === "IN_EXAM" ? <p role="status" className="text-sm text-success">Đã bắt đầu khám. Hồ sơ khám đã được hệ thống tạo.</p> : null}
      <CommandFeedback error={command.error} uncertain={command.uncertain} />
      <div className="flex flex-wrap gap-3">
        {canCheckIn ? <Button type="button" variant="primary" disabled={command.busy || command.uncertain} onClick={() => setConfirm("check-in")}>Tiếp nhận bệnh nhân</Button> : null}
        {canStart ? <Button type="button" variant="primary" disabled={command.busy || command.uncertain} onClick={() => setConfirm("start")}>Bắt đầu khám</Button> : null}
        {canEditOrCancel && !command.busy && !command.uncertain ? <Link href={`/appointments/${appointment.appointmentId}/edit`} className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Chỉnh sửa lịch hẹn</Link> : null}
        {canEditOrCancel ? <Button type="button" variant="danger" disabled={command.busy || command.uncertain} onClick={() => setConfirm("cancel")}>Hủy lịch hẹn</Button> : null}
        <Button type="button" disabled={command.busy} onClick={onRefresh}>Cập nhật trạng thái</Button>
      </div>
      {confirm ? <div className="space-y-3 rounded-lg border border-border p-4">
        <p className="text-sm">{confirm === "check-in" ? "Xác nhận tiếp nhận và mở khoản phí khám cho lịch hẹn này?" : confirm === "cancel" ? "Xác nhận hủy lịch hẹn này?" : "Xác nhận bắt đầu khám và tạo hồ sơ khám?"}</p>
        <div className="flex flex-wrap gap-2"><Button type="button" variant="primary" loading={command.busy} disabled={command.uncertain} onClick={submit}>Xác nhận</Button><Button type="button" disabled={command.busy} onClick={() => setConfirm(null)}>Quay lại</Button></div>
      </div> : null}
    </section>
  );
}
