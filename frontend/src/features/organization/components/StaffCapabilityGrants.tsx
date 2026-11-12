"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiRequestError } from "@/lib/api";
import { capabilityApi } from "../capability-api";
import type { StaffCapability, StaffCapabilityGrantDTO } from "../types";

export const capabilityLabels: Record<StaffCapability, string> = {
  LAB_RESULT_RELEASE: "Phát hành kết quả xét nghiệm",
  MEDICAL_RECORD_REVIEW: "Duyệt hồ sơ bệnh án",
};

const errorMessages: Record<string, string> = {
  CAPABILITY_GRANT_ACTIVE: "Nhân sự này đang có quyền này rồi.",
  CAPABILITY_GRANT_INELIGIBLE:
    "Không đủ điều kiện: phát hành kết quả cần tài khoản Kỹ thuật viên xét nghiệm đang hoạt động; duyệt bệnh án cần tài khoản Bác sĩ đang hoạt động.",
  CAPABILITY_GRANT_ALREADY_REVOKED: "Quyền này đã được thu hồi trước đó.",
};

function describe(cause: unknown) {
  if (cause instanceof ApiRequestError) return (cause.code && errorMessages[cause.code]) || cause.message;
  return cause instanceof Error ? cause.message : "Không thể thực hiện thao tác.";
}

const time = (value: string) => new Date(value).toLocaleString("vi-VN");
const inputClass =
  "min-h-11 rounded-lg border border-control-border bg-surface px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary";
const buttonClass =
  "inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary-hover disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";

/** ADMIN panel: grant or revoke explicit capabilities. The backend re-checks role and eligibility. */
export function StaffCapabilityGrants({ staffId }: { staffId: string }) {
  const [grants, setGrants] = useState<StaffCapabilityGrantDTO[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [capability, setCapability] = useState<StaffCapability>("LAB_RESULT_RELEASE");
  const [reason, setReason] = useState("");
  const [revoking, setRevoking] = useState<string | null>(null);
  const [revokeReason, setRevokeReason] = useState("");
  const [busy, setBusy] = useState(false);

  // State is only set when the request settles, never synchronously inside the effect.
  const load = useCallback(() => capabilityApi.list(staffId)
    .then(rows => { setGrants(rows); setLoadError(null); })
    .catch((cause: unknown) => setLoadError(describe(cause))), [staffId]);
  useEffect(() => { void load(); }, [load]);

  async function run(action: () => Promise<unknown>, done: string) {
    setBusy(true); setActionError(null); setNotice(null);
    try { await action(); setNotice(done); await load(); return true; }
    catch (cause) { setActionError(describe(cause)); return false; }
    finally { setBusy(false); }
  }

  async function grant(event: FormEvent) {
    event.preventDefault();
    if (!reason.trim()) { setNotice(null); setActionError("Nhập lý do cấp quyền."); return; }
    if (await run(() => capabilityApi.grant(staffId, capability, reason.trim()), `Đã cấp quyền ${capabilityLabels[capability]}.`)) setReason("");
  }

  async function revoke(grantId: string) {
    if (!revokeReason.trim()) { setNotice(null); setActionError("Nhập lý do thu hồi."); return; }
    if (await run(() => capabilityApi.revoke(grantId, revokeReason.trim()), "Đã thu hồi quyền.")) { setRevoking(null); setRevokeReason(""); }
  }

  return <section aria-labelledby="capability-heading" className="rounded-xl border border-border bg-surface p-5">
    <h2 id="capability-heading" className="text-base font-semibold">Quyền đặc biệt</h2>
    <p className="mt-1 text-sm text-muted-foreground">Quyền phát hành kết quả xét nghiệm và duyệt bệnh án không đi kèm vai trò; quản trị viên cấp riêng cho từng người.</p>
    {notice ? <p role="status" className="mt-3 text-sm text-success">{notice}</p> : null}
    {actionError ? <p role="alert" className="mt-3 text-sm text-danger">{actionError}</p> : null}
    {loadError ? <p role="alert" className="mt-3 text-sm text-danger">{loadError} <button type="button" className="text-primary underline" onClick={() => void load()}>Tải lại</button></p> : null}
    {grants === null && !loadError ? <p className="mt-3 text-sm text-muted-foreground">Đang tải quyền…</p> : null}
    {grants && grants.length === 0 ? <p className="mt-3 text-sm text-muted-foreground">Chưa có quyền đặc biệt nào.</p> : null}
    {grants && grants.length > 0 ? <ul className="mt-3 divide-y divide-border">{grants.map(row => <li key={row.grantId} className="py-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{capabilityLabels[row.capability] ?? row.capability}</span>
        <span className={`rounded-full px-2 py-0.5 text-xs ${row.revokedAt ? "bg-surface-muted text-muted-foreground" : "bg-success/10 text-success"}`}>{row.revokedAt ? "Đã thu hồi" : "Đang hiệu lực"}</span>
      </div>
      <p className="mt-1 text-muted-foreground">Cấp lúc {time(row.grantedAt)} — {row.grantReason}</p>
      {row.revokedAt
        ? <p className="text-muted-foreground">Thu hồi lúc {time(row.revokedAt)} — {row.revocationReason}</p>
        : revoking === row.grantId
          ? <div className="mt-2 flex flex-wrap gap-2">
              <input aria-label="Lý do thu hồi" className={inputClass} value={revokeReason} maxLength={500} onChange={e => setRevokeReason(e.target.value)} />
              <button type="button" className={buttonClass} disabled={busy} onClick={() => void revoke(row.grantId)}>Xác nhận thu hồi</button>
              <button type="button" className="text-sm underline" onClick={() => { setRevoking(null); setRevokeReason(""); }}>Hủy</button>
            </div>
          : <button type="button" className="mt-2 text-sm text-danger underline" onClick={() => { setRevoking(row.grantId); setRevokeReason(""); setActionError(null); }}>Thu hồi</button>}
    </li>)}</ul> : null}
    <form className="mt-4 flex flex-wrap items-end gap-2" onSubmit={e => void grant(e)}>
      <label className="flex flex-col gap-1 text-sm">Quyền
        <select className={inputClass} value={capability} onChange={e => setCapability(e.target.value as StaffCapability)}>
          {(Object.keys(capabilityLabels) as StaffCapability[]).map(key => <option key={key} value={key}>{capabilityLabels[key]}</option>)}
        </select>
      </label>
      <label className="flex flex-1 flex-col gap-1 text-sm">Lý do cấp
        <input className={inputClass} value={reason} maxLength={500} onChange={e => setReason(e.target.value)} />
      </label>
      <button type="submit" className={buttonClass} disabled={busy}>Cấp quyền</button>
    </form>
  </section>;
}
