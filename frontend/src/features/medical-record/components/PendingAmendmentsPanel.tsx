"use client";

import { useCallback, useSyncExternalStore } from "react";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { medicalRecordApi } from "../api";
import type { AmendmentSection, RecordAmendmentDTO } from "../types";
import { formatClinicalInstant, PanelReadState, usePanelResource } from "./record-collaboration-ui";

const arrayIsEmpty = (value: RecordAmendmentDTO[]) => value.length === 0;
const labels: Record<AmendmentSection, string> = {
  SYMPTOMS: "Triệu chứng", CHIEF_COMPLAINT: "Lý do khám", PRESENT_ILLNESS: "Bệnh sử hiện tại", PAST_HISTORY: "Tiền sử",
  ALLERGY_NOTE: "Ghi chú dị ứng của lượt", PHYSICAL_EXAM: "Khám thực thể", ADVICE: "Dặn dò",
  DISPOSITION_NOTE: "Ghi chú hướng xử trí", CLOSURE_NOTE: "Ghi chú kết thúc", DIAGNOSIS: "Mô tả chẩn đoán",
};

/** The pending endpoint intentionally has no recordId, so this queue remains read-only. */
export function PendingAmendmentsPanel() {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const read = useCallback(() => medicalRecordApi.pendingAmendments(), []);
  const { state, retry } = usePanelResource(`pending-amendments:${role ?? "none"}`, role === "DOCTOR", read, arrayIsEmpty);
  if (role !== "DOCTOR" || state.status === "hidden") return null;
  if (state.status !== "empty" && state.status !== "loaded") return <PanelReadState label="Đính chính chờ duyệt" state={state} loadingMessage="Đang tải danh sách đính chính chờ duyệt…" deniedMessage="Bạn không có quyền xem danh sách đính chính chờ duyệt." retryMessage="Không thể tải danh sách đính chính." emptyMessage="Không có đính chính đang chờ." onRetry={retry} />;

  const pending = state.status === "loaded" ? state.data : [];
  return <section role="region" aria-label="Đính chính chờ duyệt" className="mt-6 space-y-4 rounded-xl border border-border bg-surface p-5">
    <div><h2 className="text-lg font-semibold">Đính chính chờ duyệt</h2><p className="mt-1 text-sm text-muted-foreground">Danh sách hồ sơ bạn có quyền xem xét. Mở hồ sơ đã được chỉ định để xem chi tiết và quyết định.</p></div>
    {state.status === "empty" ? <p role="status" className="text-sm text-muted-foreground">Không có đính chính đang chờ.</p> : null}
    {pending.length ? <ul className="divide-y divide-border">{pending.map((amendment) => <li key={amendment.amendmentId} className="space-y-2 py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-medium">Đính chính {labels[amendment.section]}</h3><time className="text-xs text-muted-foreground">{formatClinicalInstant(amendment.requestedAt)}</time></div>
      <p className="text-sm">{amendment.originalText} <span aria-hidden="true">→</span> {amendment.amendedText}</p>
      <p className="whitespace-pre-wrap text-sm text-muted-foreground">Lý do: {amendment.reason}</p>
      <p className="break-all text-xs text-muted-foreground">Người gửi: <span className="font-mono">{amendment.authorStaffId}</span></p>
    </li>)}</ul> : null}
  </section>;
}
