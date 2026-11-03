"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { ClinicalLabOrderForm } from "@/features/medical-record/components/ClinicalLabOrderForm";
import { medicalRecordApi } from "@/features/medical-record/api";
import type { ClinicalLabOrderDTO, MedicalRecordDTO } from "@/features/medical-record/types";
import { CreateSourceLabRequest } from "@/features/lab/components/CreateSourceLabRequest";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import { AsyncState } from "@/components/ui/AsyncState";
import { PatientContextBanner } from "@/components/references/PatientContextBanner";
import { pendingLabOrder, rememberLabOrder, forgetLabOrder } from "@/features/medical-record/pending-lab-order";

// The route composes the two Vinh-owned contexts; neither feature imports the other.
export function LabOrderWorkspace({ recordId }: { recordId: string }) {
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);

  return <LabOrderWorkspaceContents key={`${recordId}:${role ?? "anonymous"}:${token ?? "anonymous"}`} recordId={recordId} />;
}

function LabOrderWorkspaceContents({ recordId }: { recordId: string }) {
  const [order, setOrder] = useState<ClinicalLabOrderDTO | null>(() => pendingLabOrder(recordId));
  const [record, setRecord] = useState<MedicalRecordDTO | null>(null);
  const [error, setError] = useState(false);
  const [orderMismatch, setOrderMismatch] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let current = true;
    medicalRecordApi.getById(recordId).then(value => {
      if (!current) return;
      if (value.recordId !== recordId || !value.patientId || !value.doctorId || !value.departmentId || !value.examinationDate) {
        setError(true); return;
      }
      setRecord(value);
    }).catch(() => { if (current) setError(true); });
    return () => { current = false; };
  }, [recordId, attempt]);
  if (error) return <AsyncState kind="error" message="Không xác minh được hồ sơ để lập chỉ định. Chưa gửi yêu cầu xét nghiệm."
    onRetry={() => { setError(false); setAttempt(value => value + 1); }} />;
  if (!record) return <AsyncState kind="loading" message="Đang xác minh bệnh nhân và hồ sơ khám…" />;
  if (record.status !== "OPEN") return <p role="status" className="mt-6 rounded-xl border border-border bg-surface p-5">Hồ sơ đã hoàn tất. Không thể lập chỉ định mới cho hồ sơ này.</p>;
  const restoredMismatch = order && (order.recordId !== record.recordId || order.patientId !== record.patientId || order.requestingDoctorId !== record.doctorId || order.requestingDepartmentId !== record.departmentId || order.careEpisodeId !== record.appointmentId || order.careEpisodeType !== "OUTPATIENT_VISIT");
  return <div className="mt-6 space-y-6">
    <PatientContextBanner label="Ngữ cảnh chỉ định xét nghiệm" patientId={record.patientId}
      departmentId={record.departmentId} doctorId={record.doctorId} date={record.examinationDate} dateLabel="Ngày khám" />
    <p className="text-sm text-muted-foreground">Gửi một lần để lập chỉ định và chuyển sang Lab. Chờ xác nhận trước khi hướng dẫn người bệnh tới thu ngân.</p>
    {orderMismatch || restoredMismatch ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">Thông tin chỉ định trả về không khớp hồ sơ đang mở. Chưa chuyển yêu cầu sang xét nghiệm; không lập lại chỉ định. Liên hệ bộ phận hỗ trợ để đối soát.</p>
    : !order ? <ClinicalLabOrderForm recordId={recordId} onCreated={value => {
      if (value.recordId !== record.recordId || value.patientId !== record.patientId
        || value.requestingDoctorId !== record.doctorId || value.requestingDepartmentId !== record.departmentId
        || value.careEpisodeId !== record.appointmentId || value.careEpisodeType !== "OUTPATIENT_VISIT") {
        setOrderMismatch(true); return;
      }
      rememberLabOrder(value);
      setOrder(value);
    }} /> : <CreateSourceLabRequest key={order.sourceOrderId} autoSubmit onCompleted={() => forgetLabOrder(recordId)} request={{
      recordId: order.recordId,
      patientId: order.patientId,
      requestingDepartmentId: order.requestingDepartmentId,
      labType: order.labType,
      requestedDate: order.requestedDate,
      sourceOrderId: order.sourceOrderId,
      careEpisodeType: order.careEpisodeType,
      careEpisodeId: order.careEpisodeId,
      priceCode: order.priceCode,
    }} />}
  </div>;
}
