"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


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
  const { localize } = useLocale();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const read = useCallback(() => medicalRecordApi.pendingAmendments(), []);
  const { state, retry } = usePanelResource(`pending-amendments:${role ?? "none"}`, role === "DOCTOR", read, arrayIsEmpty);
  if (role !== "DOCTOR" || state.status === "hidden") return null;
  if (state.status !== "empty" && state.status !== "loaded") return <PanelReadState label={localize("Đính chính chờ duyệt")} state={state} loadingMessage={localize("Đang tải danh sách đính chính chờ duyệt…")} deniedMessage="Bạn không có quyền xem danh sách đính chính chờ duyệt." retryMessage="Không thể tải danh sách đính chính." emptyMessage={localize("Không có đính chính đang chờ.")} onRetry={retry} />;

  const pending = state.status === "loaded" ? state.data : [];
  return <section role="region" aria-label={localize("Đính chính chờ duyệt")} className="mt-6 space-y-4 rounded-xl border border-border bg-surface p-5">
    <div><h2 className="text-lg font-semibold"><L10n>{"Đính chính chờ duyệt"}</L10n></h2><p className="mt-1 text-sm text-muted-foreground"><L10n>{"Danh sách hồ sơ bạn có quyền xem xét. Mở hồ sơ đã được chỉ định để xem chi tiết và quyết định."}</L10n></p></div>
    <L10nValue>{state.status === "empty" ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Không có đính chính đang chờ."}</L10n></p> : null}</L10nValue>
    <L10nValue>{pending.length ? <ul className="divide-y divide-border"><L10nValue>{pending.map((amendment) => <li key={amendment.amendmentId} className="space-y-2 py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-medium"><L10n>{"Đính chính "}</L10n><L10nValue>{labels[amendment.section]}</L10nValue></h3><time className="text-xs text-muted-foreground"><L10nValue>{formatClinicalInstant(amendment.requestedAt)}</L10nValue></time></div>
      <p className="text-sm">{amendment.originalText} <span aria-hidden="true">→</span> {amendment.amendedText}</p>
      <p className="whitespace-pre-wrap text-sm text-muted-foreground"><L10n>{"Lý do: "}</L10n>{amendment.reason}</p>
      <p className="break-all text-xs text-muted-foreground"><L10n>{"Người gửi: "}</L10n><span className="font-mono">{amendment.authorStaffId}</span></p>
    </li>)}</L10nValue></ul> : null}</L10nValue>
  </section>;
}
