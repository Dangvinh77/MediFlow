"use client";
import { AsyncState } from "@/components/ui/AsyncState";
import { useEffect, useRef, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/session";
import { prepareCommandIntent } from "@/lib/command-intent";
import { useUnsavedChanges } from "../use-unsaved-changes";
import { ReferenceText } from "@/components/references/ReferenceText";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { organizationApi } from "../api";
import type { HeadAppointmentCommand, HeadAppointmentSnapshot, StaffDTO } from "../types";

const historyTime = new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
function formatHistoryTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Thời gian chưa xác định" : historyTime.format(date);
}
const appointmentErrors: Record<string, string> = {
  HEAD_REVISION_CONFLICT: "Bổ nhiệm đã thay đổi. Hãy kiểm tra trạng thái mới rồi xác nhận lại.",
  HEAD_INELIGIBLE: "Bác sĩ chưa đủ điều kiện bổ nhiệm: cần tài khoản bác sĩ đang hoạt động, thuộc khoa lâm sàng này và chưa có nhiệm kỳ khác.",
};

export function HeadAppointments({ departmentId, onSnapshot }: { departmentId: string; onSnapshot?: (snapshot: HeadAppointmentSnapshot) => void }) {
  const [snapshot, setSnapshot] = useState<HeadAppointmentSnapshot | null>(null);
  const [staffId, setStaffId] = useState(""); const [reason, setReason] = useState("");
  const [revoke, setRevoke] = useState(false); const [preview, setPreview] = useState(false);
  const [error, setError] = useState(""); const [success, setSuccess] = useState(""); const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<HeadAppointmentCommand | null>(null);
  const [attempt, setAttempt] = useState(0); const locked = useRef(false);
  const active = useRef({ departmentId, mounted: false });
  const snapshotListener = useRef(onSnapshot);
  const readGeneration = useRef(0);
  useEffect(() => { snapshotListener.current = onSnapshot; }, [onSnapshot]);
  useEffect(() => {
    const context = { departmentId, mounted: true }; active.current = context;
    return () => { context.mounted = false; };
  }, [departmentId]);
  const { markDirty, markSaved } = useUnsavedChanges();
  const [doctors, setDoctors] = useState<StaffDTO[] | null>(null);
  const [doctorError, setDoctorError] = useState("");
  const [doctorAttempt, setDoctorAttempt] = useState(0);
  useEffect(() => {
    let alive = true;
    const session = getSessionKey();
    async function load() {
      const rows: StaffDTO[] = [];
      for (let page = 0; ; page++) {
        const batch = await organizationApi.staff({ departmentId, jobTitle: "DOCTOR", activeOnly: true, page, size: 100 });
        rows.push(...batch.content.filter(member => member.active));
        if (page + 1 >= batch.totalPages) break;
      }
      return rows;
    }
    load().then(rows => { if (alive && session === getSessionKey()) setDoctors(rows); })
      .catch(cause => { if (alive && session === getSessionKey()) setDoctorError(cause instanceof Error ? cause.message : "Không thể tải danh sách bác sĩ của khoa."); });
    return () => { alive = false; };
  }, [departmentId, doctorAttempt]);
  function toggleDoctor(id: string) {
    markDirty(); setRevoke(false); setPreview(false);
    // One department has one head: checking a doctor clears any other choice.
    setStaffId(current => current === id ? "" : id);
  }
  useEffect(() => {
    let alive = true;
    const session = getSessionKey();
    const generation = ++readGeneration.current;
    const isCurrent = () => alive && generation === readGeneration.current && session === getSessionKey();
    organizationApi.headAppointments(departmentId).then(value => {
      if (!isCurrent() || value.departmentId !== departmentId) return;
      setSnapshot(value);
      snapshotListener.current?.(value);
    }).catch(cause => {
      if (isCurrent()) setError(cause instanceof Error ? cause.message : "Không thể tải bổ nhiệm.");
    });
    return () => { alive = false; };
  }, [departmentId, attempt]);
  async function save() {
    if (!snapshot || locked.current) return;
    const body = pending ?? { staffId: revoke ? null : staffId, expectedRevision: snapshot.revision, reason: reason.trim() };
    if (!body.reason || (!revoke && !body.staffId)) return;
    locked.current = true; setBusy(true); setError(""); setSuccess(""); const session = getSessionKey();
    const context = active.current;
    const isCurrent = () => context.mounted && active.current === context && context.departmentId === departmentId && session === getSessionKey();
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | undefined;
    try {
      intent = await prepareCommandIntent(`head-appointment:${departmentId}`, session, body);
      if (!isCurrent()) { intent.cancelUnsent(); return; }
      const result = await organizationApi.replaceHeadAppointment(departmentId, body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      if (!isCurrent() || result.departmentId !== departmentId) return;
      // A read started before this write cannot replace its authoritative result.
      readGeneration.current += 1;
      setSnapshot(result); setPending(null); setPreview(false); setStaffId(""); setReason(""); markSaved();
      setSuccess(body.staffId ? "Đã bổ nhiệm trưởng khoa." : "Đã miễn nhiệm trưởng khoa.");
      snapshotListener.current?.(result);
    } catch (cause) {
      intent?.settle(cause); if (!isCurrent()) return;
      const unknown = !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500 || cause.code === "IDEMPOTENCY_CONFLICT";
      if (unknown) setPending(body);
      setError(cause instanceof ApiRequestError && cause.code && appointmentErrors[cause.code] ? appointmentErrors[cause.code] : cause instanceof Error ? cause.message : "Không thể lưu bổ nhiệm.");
      if (cause instanceof ApiRequestError && cause.code === "HEAD_REVISION_CONFLICT") { setSnapshot(null); setPreview(false); setAttempt(value => value + 1); }
    } finally { if (active.current === context) { locked.current = false; if (isCurrent()) setBusy(false); } }
  }
  return <section className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <h2 className="text-lg font-semibold">Bổ nhiệm trưởng khoa</h2>
    {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
    {success ? <p role="status" className="text-sm text-success">{success}</p> : null}
    {!snapshot ? <><AsyncState kind="loading" message="Đang tải bổ nhiệm…" />{error ? <Button onClick={() => { setError(""); setAttempt(value => value + 1); }}>Tải lại bổ nhiệm</Button> : null}</> : <>
      <p className="text-sm">Trưởng khoa đương nhiệm</p>
      {snapshot.staffId ? <ReferenceText kind="doctor" value={snapshot.staffId} /> : <p className="text-sm text-muted-foreground">Chưa có trưởng khoa đương nhiệm.</p>}
      <p className="text-xs text-muted-foreground">Phiên bản {snapshot.revision}</p>
      <form onSubmit={event => { event.preventDefault(); void save(); }} onChangeCapture={markDirty} className="space-y-4">
        <fieldset disabled={busy || Boolean(pending)} className="space-y-3">
          <legend className="text-sm font-medium">Bác sĩ của khoa</legend>
          <p className="text-xs text-muted-foreground">Đánh dấu một bác sĩ rồi bấm Bổ nhiệm. Chỉ bác sĩ có tài khoản đang hoạt động mới đủ điều kiện; mỗi khoa chỉ có một trưởng khoa.</p>
          {doctorError ? <AsyncState kind="error" message={doctorError} onRetry={() => { setDoctorError(""); setDoctors(null); setDoctorAttempt(value => value + 1); }} />
            : !doctors ? <AsyncState kind="loading" message="Đang tải bác sĩ của khoa…" />
            : doctors.length === 0 ? <AsyncState kind="empty" message="Khoa chưa có bác sĩ đang làm việc." />
            : <div className="overflow-x-auto rounded-lg border border-border"><table className="w-full text-left text-sm">
              <caption className="sr-only">Chọn bác sĩ để bổ nhiệm hoặc miễn nhiệm</caption>
              <thead className="bg-surface-muted text-muted-foreground"><tr><th scope="col" className="w-12 px-3 py-2"><span className="sr-only">Chọn</span></th><th scope="col" className="px-3 py-2">Họ tên</th><th scope="col" className="px-3 py-2">Chuyên môn</th><th scope="col" className="px-3 py-2">Chức vụ</th></tr></thead>
              <tbody>{doctors.map(doctor => <tr key={doctor.staffId} className={`border-t border-border ${staffId === doctor.staffId ? "bg-primary/5" : ""}`}>
                <td className="px-3 py-2"><input type="checkbox" aria-label={`Chọn ${doctor.fullName}`} checked={staffId === doctor.staffId} onChange={() => toggleDoctor(doctor.staffId)} className="size-5 accent-primary" /></td>
                <td className="px-3 py-2 font-medium">{doctor.fullName}</td>
                <td className="px-3 py-2">{doctor.specialization || "Chưa cập nhật"}</td>
                <td className="px-3 py-2">{snapshot.staffId === doctor.staffId ? <StatusBadge tone="success">Trưởng khoa đương nhiệm</StatusBadge> : <span className="text-muted-foreground">Bác sĩ</span>}</td>
              </tr>)}</tbody>
            </table></div>}
          <label className="block space-y-1 text-sm">Lý do<textarea aria-label="Lý do" required maxLength={500} value={reason} onChange={event => { setReason(event.target.value); setPreview(false); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label>
          <div className="flex flex-wrap gap-3"><Button type="button" variant="primary" disabled={!staffId || staffId === snapshot.staffId || !reason.trim()} onClick={() => { setRevoke(false); setPreview(true); }}>Bổ nhiệm</Button>{snapshot.staffId ? <Button type="button" variant="secondary" onClick={() => { setRevoke(true); setPreview(true); markDirty(); }}>Miễn nhiệm</Button> : null}</div>
        </fieldset>
        {preview || pending ? <div className="space-y-3 rounded-lg border border-border p-4"><p className="text-sm">{snapshot.staffId ? "Nhiệm kỳ hiện tại sẽ kết thúc khi xác nhận." : "Bắt đầu nhiệm kỳ trưởng khoa mới."}</p>{snapshot.staffId ? <div className="text-sm"><p>Người kết thúc nhiệm kỳ</p><ReferenceText kind="doctor" value={snapshot.staffId} /></div> : null}{!revoke && staffId ? <div className="text-sm"><p>Người được bổ nhiệm</p><ReferenceText kind="doctor" value={staffId} /></div> : null}<p className="whitespace-pre-wrap break-words text-sm">Lý do xác nhận: {pending?.reason ?? reason.trim()}</p>{pending ? <p role="status" className="text-sm">Kết quả chưa xác định. Chỉ gửi lại cùng yêu cầu để đối soát; nội dung đang được giữ nguyên.</p> : null}<Button type="submit" loading={busy} disabled={!reason.trim() || (!revoke && !staffId)}>{pending ? "Gửi lại cùng yêu cầu" : revoke ? "Xác nhận miễn nhiệm" : "Xác nhận bổ nhiệm"}</Button></div> : null}
      </form>
      <details><summary className="cursor-pointer text-sm font-medium">Lịch sử bổ nhiệm ({snapshot.history.length})</summary><p className="mt-2 text-xs text-muted-foreground">Giờ Việt Nam (UTC+7)</p><ul className="mt-3 space-y-2">{snapshot.history.map(item => <li key={item.appointmentId} className="rounded-lg border border-border p-3 text-sm"><ReferenceText kind="doctor" value={item.staffId} /><p className="mt-1 text-muted-foreground">{formatHistoryTime(item.startedAt)} – {item.endedAt ? formatHistoryTime(item.endedAt) : "Đang hiệu lực"}</p></li>)}</ul></details>
    </>}
  </section>;
}
