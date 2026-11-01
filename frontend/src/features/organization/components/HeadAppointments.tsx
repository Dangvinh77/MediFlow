"use client";
import { useEffect, useRef, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/session";
import { prepareCommandIntent } from "@/lib/command-intent";
import { useUnsavedChanges } from "../use-unsaved-changes";
import { ReferenceText } from "@/components/references/ReferenceText";
import { Button } from "@/components/ui/Button";
import { StaffPicker } from "./StaffPicker";
import { organizationApi } from "../api";
import type { HeadAppointmentCommand, HeadAppointmentSnapshot } from "../types";

export function HeadAppointments({ departmentId }: { departmentId: string }) {
  const [snapshot, setSnapshot] = useState<HeadAppointmentSnapshot | null>(null);
  const [staffId, setStaffId] = useState(""); const [reason, setReason] = useState("");
  const [revoke, setRevoke] = useState(false); const [preview, setPreview] = useState(false);
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<HeadAppointmentCommand | null>(null);
  const [attempt, setAttempt] = useState(0); const locked = useRef(false);
  const { markDirty, markSaved } = useUnsavedChanges();
  useEffect(() => { let alive = true; const session = getSessionKey(); organizationApi.headAppointments(departmentId).then(value => { if (alive && session === getSessionKey()) setSnapshot(value); }).catch(cause => { if (alive && session === getSessionKey()) setError(cause instanceof Error ? cause.message : "Không thể tải bổ nhiệm."); }); return () => { alive = false; }; }, [departmentId, attempt]);
  async function save() {
    if (!snapshot || locked.current) return;
    const body = pending ?? { staffId: revoke ? null : staffId, expectedRevision: snapshot.revision, reason: reason.trim() };
    if (!body.reason || (!revoke && !body.staffId)) return;
    locked.current = true; setBusy(true); setError(""); const session = getSessionKey();
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | undefined;
    try {
      intent = await prepareCommandIntent(`head-appointment:${departmentId}`, session, body);
      if (session !== getSessionKey()) return;
      const result = await organizationApi.replaceHeadAppointment(departmentId, body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      if (session !== getSessionKey()) return;
      setSnapshot(result); setPending(null); setPreview(false); setStaffId(""); setReason(""); markSaved();
    } catch (cause) {
      intent?.settle(cause); if (session !== getSessionKey()) return;
      const unknown = !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500 || cause.code === "IDEMPOTENCY_CONFLICT";
      if (unknown) setPending(body);
      setError(cause instanceof Error ? cause.message : "Không thể lưu bổ nhiệm.");
      if (cause instanceof ApiRequestError && cause.code === "HEAD_REVISION_CONFLICT") { setSnapshot(null); setPreview(false); setAttempt(value => value + 1); }
    } finally { locked.current = false; setBusy(false); }
  }
  return <section className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <h2 className="text-lg font-semibold">Bổ nhiệm trưởng khoa</h2>
    {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
    {!snapshot ? <><p role="status">Đang tải bổ nhiệm…</p>{error ? <Button onClick={() => { setError(""); setAttempt(value => value + 1); }}>Tải lại bổ nhiệm</Button> : null}</> : <>
      <p className="text-sm">Trưởng khoa đương nhiệm</p>
      {snapshot.staffId ? <ReferenceText kind="doctor" value={snapshot.staffId} /> : <p className="text-sm text-muted-foreground">Chưa có trưởng khoa đương nhiệm.</p>}
      <p className="text-xs text-muted-foreground">Phiên bản {snapshot.revision}</p>
      <form onSubmit={event => { event.preventDefault(); void save(); }} onChangeCapture={markDirty} className="space-y-4">
        <fieldset disabled={busy || Boolean(pending)} className="space-y-3">
          <StaffPicker departmentId={departmentId} jobTitle="DOCTOR" value={staffId} onChange={value => { markDirty(); setStaffId(value); setRevoke(false); setPreview(false); }} label="Bác sĩ được đề cử" />
          <label className="block space-y-1 text-sm">Lý do<textarea aria-label="Lý do" required maxLength={500} value={reason} onChange={event => { setReason(event.target.value); setPreview(false); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label>
          <div className="flex flex-wrap gap-3"><Button type="button" disabled={!staffId || !reason.trim()} onClick={() => { setRevoke(false); setPreview(true); }}>Xem trước bổ nhiệm</Button>{snapshot.staffId ? <Button type="button" variant="secondary" onClick={() => { setRevoke(true); setPreview(true); markDirty(); }}>Miễn nhiệm</Button> : null}</div>
        </fieldset>
        {preview || pending ? <div className="space-y-3 rounded-lg border border-border p-4"><p className="text-sm">{snapshot.staffId ? "Nhiệm kỳ hiện tại sẽ kết thúc khi xác nhận." : "Bắt đầu nhiệm kỳ trưởng khoa mới."}</p>{!revoke && staffId ? <ReferenceText kind="doctor" value={staffId} /> : null}{pending ? <p role="status" className="text-sm">Kết quả chưa xác định. Chỉ gửi lại cùng yêu cầu để đối soát; nội dung đang được giữ nguyên.</p> : null}<Button type="submit" loading={busy} disabled={!reason.trim() || (!revoke && !staffId)}>{pending ? "Gửi lại cùng yêu cầu" : revoke ? "Xác nhận miễn nhiệm" : "Xác nhận bổ nhiệm"}</Button></div> : null}
      </form>
      <details><summary className="cursor-pointer text-sm font-medium">Lịch sử bổ nhiệm ({snapshot.history.length})</summary><ul className="mt-3 space-y-2">{snapshot.history.map(item => <li key={item.appointmentId} className="rounded-lg border border-border p-3 text-sm"><ReferenceText kind="doctor" value={item.staffId} /><p className="mt-1 text-muted-foreground">{item.startedAt} – {item.endedAt ?? "Đang hiệu lực"}</p></li>)}</ul></details>
    </>}
  </section>;
}
