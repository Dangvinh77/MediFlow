"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { Fragment, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { CommandIntentError, prepareCommandIntent } from "@/lib/command-intent";
import { formatInstant } from "@/lib/format";
import { getSessionKey } from "@/lib/session";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ReferenceText } from "@/components/references/ReferenceText";
import { appointmentApi } from "../api";
import { queueNumberText, roomNumberText } from "../presentation";
import { vietnamToday } from "../session-dates";
import { ScheduleDateInput } from "./ScheduleDateInput";
import { VitalSignForm } from "./VitalSignForm";
import { VitalSignHistory } from "./VitalSignHistory";
import type { ClinicQueueEntryDTO, ClinicQueueStage, ClinicQueueTicketDTO, ClinicTicketAdjustmentType } from "../types";

interface DeskError { message: string; correlationId: string | null; denied?: boolean }
interface DeskCommand { scope: string; body: object; send: (options: { idempotencyKey: string; correlationId: string }) => Promise<ClinicQueueTicketDTO>; done: (ticket: ClinicQueueTicketDTO) => string }
interface Adjusting { registrationId: string; type: ClinicTicketAdjustmentType }

const DENIED = "Chỉ điều dưỡng của khoa này mới dùng được quầy khoa. Điều dưỡng tiếp nhận vui lòng dùng màn Tiếp nhận ca khám.";
const messages: Record<string, string> = {
  CLINIC_DESK_SCOPE_DENIED: DENIED,
  ROOM_TICKET_EXISTS: "Bệnh nhân này đã có số phòng. Danh sách đã được tải lại.",
  ROOM_TICKET_NOT_ELIGIBLE: "Chỉ cấp số phòng cho bệnh nhân đã thanh toán và đã đến quầy tiếp nhận.",
  ROOM_TICKET_WRONG_DATE: "Chỉ cấp số phòng trong đúng ngày khám.",
  TICKET_STATE_CONFLICT: "Lượt này vừa đổi trạng thái nên thao tác không còn phù hợp. Danh sách đã được tải lại.",
  REVISION_CONFLICT: "Lượt này vừa được cập nhật ở nơi khác. Danh sách đã được tải lại.",
};
const adjustmentLabels: Record<ClinicTicketAdjustmentType, string> = { LATE: "Đến muộn", NO_SHOW: "Vắng mặt", RETURNED: "Đã quay lại", PRIORITY: "Ưu tiên" };
const groups: readonly { title: string; stages: readonly ClinicQueueStage[] }[] = [
  { title: "Chờ cấp số phòng", stages: ["WAITING_ROOM_TICKET"] },
  { title: "Được vào khám", stages: ["WAITING_CALL", "CALLED"] },
  { title: "Đang khám", stages: ["IN_EXAM"] },
  { title: "Vắng mặt", stages: ["ABSENT"] },
  { title: "Chưa đủ điều kiện", stages: ["AWAITING_PAYMENT", "NOT_ARRIVED"] },
];

function deskError(cause: unknown, fallback: string): DeskError {
  if (cause instanceof ApiRequestError) return { message: messages[cause.code ?? ""] ?? cause.message, correlationId: cause.correlationId, denied: cause.code === "CLINIC_DESK_SCOPE_DENIED" };
  return { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

function outcomeUnknown(cause: unknown) {
  return cause instanceof CommandIntentError || !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

export function ClinicQueueWorkspace({ enabled }: { enabled: boolean }) {
  const { localize } = useLocale();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const [date, setDate] = useState(vietnamToday);
  const [entries, setEntries] = useState<ClinicQueueEntryDTO[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<DeskError | null>(null);
  const [busy, setBusy] = useState(false);
  const [commandError, setCommandError] = useState<DeskError | null>(null);
  const [pending, setPending] = useState<DeskCommand | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [adjusting, setAdjusting] = useState<Adjusting | null>(null);
  const [reason, setReason] = useState("");
  const [recording, setRecording] = useState<string | null>(null);
  const [viewing, setViewing] = useState<string | null>(null);
  const [historyVersion, setHistoryVersion] = useState(0);
  const reads = useRef(0);
  const lock = useRef(false);
  const alive = useRef(true);

  useEffect(() => { alive.current = true; const generation = reads; return () => { alive.current = false; generation.current++; }; }, []);

  async function load(day = date) {
    const generation = ++reads.current;
    setLoading(true); setLoadError(null);
    try {
      const result = await appointmentApi.clinicQueue(day);
      if (generation === reads.current) setEntries(result);
    } catch (cause) {
      if (generation === reads.current) setLoadError(deskError(cause, "Không thể tải hàng đợi."));
    } finally {
      if (generation === reads.current) setLoading(false);
    }
  }

  useEffect(() => {
    if (!enabled) return;
    const timer = setTimeout(() => { if (alive.current) void load(); }, 0);
    return () => clearTimeout(timer);
    // Initial load only; later reads follow an explicit action.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);

  async function run(command: DeskCommand) {
    if (lock.current) return;
    lock.current = true; setBusy(true); setCommandError(null); setNotice(null);
    const session = getSessionKey();
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | undefined;
    try {
      intent = await prepareCommandIntent(command.scope, session, command.body);
      const ticket = await command.send({ idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      if (!alive.current || session !== getSessionKey()) return;
      setPending(null); setAdjusting(null); setReason(""); setNotice(command.done(ticket));
      void load();
    } catch (cause) {
      intent?.settle(cause);
      if (!alive.current || session !== getSessionKey()) return;
      const unknown = outcomeUnknown(cause);
      setPending(unknown ? command : null);
      setCommandError(deskError(cause, "Chưa xác định được kết quả thao tác."));
      if (!unknown) void load();
    } finally {
      lock.current = false;
      if (alive.current) setBusy(false);
    }
  }

  function issue(entry: ClinicQueueEntryDTO) {
    void run({
      scope: `clinic-room-ticket:${entry.registrationId}`, body: { registrationId: entry.registrationId },
      send: options => appointmentApi.issueRoomTicket(entry.registrationId, options),
      done: ticket => `Đã cấp số phòng ${ticket.roomQueueNumber}.`,
    });
  }

  function call(entry: ClinicQueueEntryDTO) {
    if (!entry.ticketId || entry.ticketRevision === null) return;
    const ticketId = entry.ticketId;
    const body = { expectedRevision: entry.ticketRevision };
    void run({
      scope: `clinic-ticket-call:${ticketId}`, body,
      send: options => appointmentApi.callTicket(ticketId, body, options),
      done: ticket => `Đã gọi số phòng ${ticket.roomQueueNumber}.`,
    });
  }

  function adjust(entry: ClinicQueueEntryDTO, type: ClinicTicketAdjustmentType) {
    if (!entry.ticketId || entry.ticketRevision === null || !reason.trim()) return;
    const ticketId = entry.ticketId;
    const body = { expectedRevision: entry.ticketRevision, type, reason: reason.trim() };
    void run({
      scope: `clinic-ticket-adjust:${ticketId}:${type}`, body,
      send: options => appointmentApi.adjustTicket(ticketId, body, options),
      done: () => `Đã ghi nhận: ${adjustmentLabels[type]}.`,
    });
  }

  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground"><L10n>{"Quầy điều dưỡng khoa chưa được mở."}</L10n></p>;

  const locked = busy || Boolean(pending);
  const rooms = [...new Set((entries ?? []).map(entry => entry.roomId))];

  function actionButton(entry: ClinicQueueEntryDTO, type: ClinicTicketAdjustmentType) {
    return <Button key={type} type="button" variant="secondary" disabled={locked} onClick={() => { setReason(""); setAdjusting({ registrationId: entry.registrationId, type }); }}><L10nValue>{adjustmentLabels[type]}</L10nValue></Button>;
  }

  function row(entry: ClinicQueueEntryDTO) {
    const inLine = entry.stage === "WAITING_CALL" || entry.stage === "CALLED";
    const room = roomNumberText(entry.roomQueueNumber);
    const active = adjusting?.registrationId === entry.registrationId ? adjusting : null;
    const expanded = Boolean(active) || recording === entry.registrationId || viewing === entry.registrationId;
    return <Fragment key={entry.registrationId}>
      <tr className={`border-t border-border align-top ${expanded ? "bg-primary/5" : ""}`}>
        <td className="whitespace-nowrap px-3 py-3 tabular-nums"><span className="block font-semibold"><L10nValue>{queueNumberText(entry.queueNumber, true)}</L10nValue></span><L10nValue>{room ? <span className="block text-xs text-muted-foreground"><L10nValue>{room}</L10nValue></span> : null}</L10nValue></td>
        <td className="px-3 py-3"><span className="font-medium"><ReferenceText kind="patient" value={entry.patientId} fallback="Bệnh nhân" /></span>
          <L10nValue>{entry.priority ? <span className="mt-1 block"><StatusBadge tone="warning"><L10n>{"Được ưu tiên"}</L10n></StatusBadge></span> : null}</L10nValue></td>
        <td className="px-3 py-3"><L10nValue>{entry.checkedInAt ? <StatusBadge tone={entry.vitalSignsRecorded ? "success" : "neutral"}><L10nValue>{entry.vitalSignsRecorded ? "Đã có sinh hiệu" : "Chưa có sinh hiệu"}</L10nValue></StatusBadge> : <span className="text-muted-foreground">—</span>}</L10nValue>
          <L10nValue>{entry.calledAt ? <span className="mt-1 block text-xs text-muted-foreground"><L10n>{"Đã gọi lúc "}</L10n><L10nValue>{formatInstant(entry.calledAt)}</L10nValue></span> : null}</L10nValue></td>
        <td className="px-3 py-3"><div className="flex flex-wrap gap-2 [&_button]:whitespace-nowrap">
          <L10nValue>{entry.stage === "WAITING_ROOM_TICKET" && role === "NURSE" ? <Button type="button" variant="primary" disabled={locked} onClick={() => issue(entry)}><L10n>{"Cấp số phòng"}</L10n></Button> : null}</L10nValue>
          <L10nValue>{inLine ? <Button type="button" variant="primary" disabled={locked} onClick={() => call(entry)}><L10nValue>{entry.stage === "CALLED" ? "Gọi lại" : "Gọi vào khám"}</L10nValue></Button> : null}</L10nValue>
          <L10nValue>{inLine ? actionButton(entry, "LATE") : null}</L10nValue>
          <L10nValue>{inLine ? actionButton(entry, "NO_SHOW") : null}</L10nValue>
          <L10nValue>{entry.stage === "ABSENT" ? actionButton(entry, "RETURNED") : null}</L10nValue>
          <L10nValue>{(inLine || entry.stage === "ABSENT") && !entry.priority ? actionButton(entry, "PRIORITY") : null}</L10nValue>
          <L10nValue>{entry.checkedInAt && role === "NURSE" ? <Button type="button" variant="secondary" aria-expanded={recording === entry.registrationId} disabled={locked} onClick={() => setRecording(current => current === entry.registrationId ? null : entry.registrationId)}><L10n>{"Ghi sinh hiệu"}</L10n></Button> : null}</L10nValue>
          <L10nValue>{entry.checkedInAt ? <Button type="button" variant="secondary" aria-expanded={viewing === entry.registrationId} onClick={() => setViewing(current => current === entry.registrationId ? null : entry.registrationId)}><L10n>{"Xem sinh hiệu"}</L10n></Button> : null}</L10nValue>
        </div></td>
      </tr>
      <L10nValue>{entry.continuationRequired || expanded ? <tr className="bg-primary/5"><td colSpan={4} className="space-y-3 px-3 pb-4">
        <L10nValue>{entry.continuationRequired ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-2"><L10n>{"Buổi khám đã hết giờ nhưng lượt khám còn mở, cần xử lý tiếp."}</L10n></p> : null}</L10nValue>
        <L10nValue>{active ? <form className="space-y-2 rounded-lg border border-border bg-surface p-3" onSubmit={event => { event.preventDefault(); adjust(entry, active.type); }}>
          <p className="font-medium"><L10nValue>{adjustmentLabels[active.type]}</L10nValue></p>
          <label className="block space-y-1" htmlFor={`clinic-reason-${entry.registrationId}`}><L10n>{"Lý do"}</L10n><textarea id={`clinic-reason-${entry.registrationId}`} maxLength={500} disabled={locked} value={reason} onChange={event => setReason(event.target.value)} className="min-h-16 w-full rounded-lg border border-control-border bg-surface px-3 py-2" />
          </label>
          <div className="flex flex-wrap gap-2"><Button type="submit" variant="primary" loading={busy} disabled={locked || !reason.trim()}><L10n>{"Xác nhận"}</L10n></Button><Button type="button" disabled={locked} onClick={() => setAdjusting(null)}><L10n>{"Quay lại"}</L10n></Button></div>
        </form> : null}</L10nValue>
        <L10nValue>{recording === entry.registrationId ? <VitalSignForm registrationId={entry.registrationId} onSaved={() => { setRecording(null); setHistoryVersion(value => value + 1); setNotice("Đã lưu sinh hiệu."); void load(); }} /> : null}</L10nValue>
        <L10nValue>{viewing === entry.registrationId ? <VitalSignHistory registrationId={entry.registrationId} refreshKey={historyVersion} /> : null}</L10nValue>
      </td></tr> : null}</L10nValue>
    </Fragment>;
  }

  return <div className="space-y-5">
    <form noValidate aria-label={localize("Chọn ngày hàng đợi")} className="flex flex-wrap items-end gap-3 rounded-xl border border-border bg-surface p-5" onSubmit={event => { event.preventDefault(); void load(); }}>
      <ScheduleDateInput label={localize("Ngày khám")} required disabled={locked} value={date} onChange={event => { reads.current++; setLoading(false); setEntries(null); setDate(event.target.value); }} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" />
      <Button type="submit" loading={loading} disabled={locked || !date}><L10n>{"Tải hàng đợi"}</L10n></Button>
    </form>
    <L10nValue>{loadError ? loadError.denied
      ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><L10nValue>{loadError.message}</L10nValue></p>
      : <AsyncState kind="error" message={localize(loadError.message)} correlationId={loadError.correlationId} onRetry={() => void load()} /> : null}</L10nValue>
    <L10nValue>{notice ? <p role="status" className="rounded-lg border border-primary/30 bg-primary/5 p-3 text-sm"><L10nValue>{notice}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{commandError ? <div role="alert" className="space-y-2 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p><L10nValue>{commandError.message}</L10nValue></p>
      <L10nValue>{commandError.correlationId ? <p className="text-xs"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{commandError.correlationId}</span></p> : null}</L10nValue>
      <L10nValue>{pending ? <><p><L10n>{"Chưa xác định thao tác đã thực hiện hay chưa. Gửi lại đúng yêu cầu để tránh xử lý trùng."}</L10n></p><Button type="button" loading={busy} onClick={() => void run(pending)}><L10n>{"Gửi lại cùng yêu cầu"}</L10n></Button></> : null}</L10nValue>
    </div> : null}</L10nValue>
    <L10nValue>{entries && !entries.length && !loading ? <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground"><L10n>{"Không có bệnh nhân trong hàng đợi của ngày này."}</L10n></p> : null}</L10nValue>
    <L10nValue>{rooms.map(roomId => {
      const roomEntries = (entries ?? []).filter(entry => entry.roomId === roomId);
      const headingId = `clinic-room-${roomId}`;
      return <section key={roomId} aria-labelledby={headingId} className="space-y-3 rounded-xl border border-border bg-surface p-5">
        <h2 id={headingId} className="text-lg font-semibold"><ReferenceText kind="room" value={roomId} fallback="Phòng khám" /></h2>
        <L10nValue>{groups.map(group => {
          const items = roomEntries.filter(entry => group.stages.includes(entry.stage));
          if (!items.length) return null;
          return <div key={group.title} className="space-y-2"><h3 className="font-semibold"><L10nValue>{group.title}</L10nValue> <span className="text-sm font-normal text-muted-foreground">({items.length})</span></h3>
            <div className="overflow-x-auto rounded-lg border border-border"><table aria-label={localize(group.title)} className="w-full min-w-2xl table-fixed text-left text-sm">
              <colgroup><col className="w-28" /><col className="w-[28%]" /><col className="w-36" /><col /></colgroup>
              <thead className="bg-surface-muted text-muted-foreground"><tr>
                <th scope="col" className="px-3 py-2"><L10n>{"Số thứ tự"}</L10n></th>
                <th scope="col" className="px-3 py-2"><L10n>{"Bệnh nhân"}</L10n></th>
                <th scope="col" className="px-3 py-2"><L10n>{"Sinh hiệu"}</L10n></th>
                <th scope="col" className="px-3 py-2"><L10n>{"Thao tác"}</L10n></th>
              </tr></thead>
              <tbody><L10nValue>{items.map(row)}</L10nValue></tbody>
            </table></div></div>;
        })}</L10nValue>
      </section>;
    })}</L10nValue>
  </div>;
}
