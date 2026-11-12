"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import { medicalRecordApi } from "../api";
import type { RecordAccessPurpose } from "../types";

const purposeLabels: Record<RecordAccessPurpose, string> = {
  CONSULTATION: "Khám bệnh",
  CARE_CONTINUITY: "Tiếp nối điều trị",
  QUALITY_REVIEW: "Đánh giá chất lượng",
  EMERGENCY: "Cấp cứu",
  RECORD_MANAGEMENT: "Quản lý hồ sơ",
};

const purposesByRole: Partial<Record<Role, RecordAccessPurpose[]>> = {
  DOCTOR: ["CONSULTATION", "CARE_CONTINUITY", "QUALITY_REVIEW", "EMERGENCY"],
  ADMIN: ["RECORD_MANAGEMENT", "QUALITY_REVIEW"],
  NURSE: ["EMERGENCY"],
};

const MIN_REASON_LENGTH = 10;
const needsReason = (purpose: RecordAccessPurpose | null) => purpose === "QUALITY_REVIEW" || purpose === "RECORD_MANAGEMENT";

/** The server decides whether a declaration opens the record; this form only collects it. */
export function RecordAccessPurposeForm({ patientId, role, onDeclared }: { patientId: string; role: Role | null; onDeclared: () => void }) {
  const [purpose, setPurpose] = useState<RecordAccessPurpose | null>(null);
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const purposes = role ? purposesByRole[role] ?? [] : [];
  const reasonRequired = needsReason(purpose);
  const reasonReady = !reasonRequired || reason.trim().length >= MIN_REASON_LENGTH;

  async function submit() {
    if (!purpose || !reasonReady || saving) return;
    setSaving(true);
    setError(null);
    try {
      await medicalRecordApi.declareAccess({ patientId, purpose, reason: reasonRequired ? reason.trim() : null });
      onDeclared();
    } catch (cause: unknown) {
      setError(cause instanceof ApiRequestError || cause instanceof Error ? cause.message : "Không thể ghi nhận mục đích.");
    } finally {
      setSaving(false);
    }
  }

  return <form className="mt-4 max-w-xl space-y-4" onSubmit={(event) => { event.preventDefault(); void submit(); }}>
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium"><L10n>{"Mục đích xem hồ sơ"}</L10n></legend>
      <L10nValue>{purposes.map((option) => <label key={option} className="flex min-h-11 items-center gap-2 text-sm">
        <input type="radio" name="record-access-purpose" value={option} checked={purpose === option} onChange={() => { setPurpose(option); setError(null); }} />
        <L10nValue>{purposeLabels[option]}</L10nValue>
      </label>)}</L10nValue>
    </fieldset>
    <L10nValue>{reasonRequired ? <label className="block space-y-1 text-sm font-medium" htmlFor="record-access-reason"><L10n>{"Lý do (ít nhất "}</L10n><L10nValue>{MIN_REASON_LENGTH}</L10nValue><L10n>{" ký tự)"}</L10n><textarea id="record-access-reason" value={reason} maxLength={500} onChange={(event) => { setReason(event.target.value); setError(null); }} className="min-h-24 w-full rounded-lg border border-border bg-surface px-3 py-2 font-normal text-foreground" />
    </label> : null}</L10nValue>
    <L10nValue>{error ? <p role="alert" className="text-sm text-danger"><L10nValue>{error}</L10nValue></p> : null}</L10nValue>
    <Button type="submit" loading={saving} disabled={!purpose || !reasonReady}><L10n>{"Xác nhận mục đích"}</L10n></Button>
  </form>;
}
