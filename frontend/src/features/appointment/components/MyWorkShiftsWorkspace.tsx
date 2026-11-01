"use client";
import { useEffect, useRef, useState } from "react";
import { getSessionKey } from "@/lib/session";
import { ReferenceText } from "@/components/references/ReferenceText";
import { appointmentApi } from "../api";
import { vietnamToday } from "../session-dates";
import type { DoctorWorkShiftDTO } from "../types";

export function shiftRange(date: string, view: "day" | "week") {
  if (view === "day") return { fromDate: date, toDate: date };
  const value = new Date(`${date}T12:00:00Z`); const day = value.getUTCDay();
  value.setUTCDate(value.getUTCDate() - (day === 0 ? 6 : day - 1));
  const fromDate = value.toISOString().slice(0, 10); value.setUTCDate(value.getUTCDate() + 6);
  return { fromDate, toDate: value.toISOString().slice(0, 10) };
}
export function MyWorkShiftsWorkspace({ enabled }: { enabled: boolean }) {
  const [date, setDate] = useState(vietnamToday); const [view, setView] = useState<"day" | "week">("day");
  const [rows, setRows] = useState<DoctorWorkShiftDTO[]>([]); const [busy, setBusy] = useState(enabled);
  const [error, setError] = useState(""); const reads = useRef(0);
  useEffect(() => {
    if (!enabled || !date) return;
    const generation = ++reads.current; const session = getSessionKey(); const {fromDate, toDate} = shiftRange(date,view);
    Promise.resolve().then(() => { if (generation === reads.current) { setBusy(true); setError(""); setRows([]); } });
    appointmentApi.myWorkShifts(fromDate,toDate).then(value => { if (generation === reads.current && session === getSessionKey()) setRows(value); })
      .catch(cause => { if (generation === reads.current && session === getSessionKey()) setError(cause instanceof Error ? cause.message : "Không thể tải lịch làm việc."); })
      .finally(() => { if (generation === reads.current && session === getSessionKey()) setBusy(false); });
    const currentReads = reads; return () => { currentReads.current++; };
  },[enabled,date,view]);
  if (!enabled) return <p role="status">Lịch làm việc chưa được bật.</p>;
  return <section className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold">Lịch làm việc của tôi</h2><p className="text-sm text-muted-foreground">Phân công giờ và phòng của bạn. Ca làm việc và buổi khám được quản lý riêng.</p><div className="flex flex-wrap gap-4"><label className="text-sm">Chế độ lịch<select value={view} onChange={event => setView(event.target.value as "day"|"week")} className="block min-h-11 rounded-lg border border-control-border bg-surface px-3"><option value="day">Theo ngày</option><option value="week">Theo tuần</option></select></label><label className="text-sm">Ngày tham chiếu<input type="date" required value={date} onChange={event => setDate(event.target.value)} className="block min-h-11 rounded-lg border border-control-border bg-surface px-3" /></label></div>{busy ? <p role="status">Đang tải lịch làm việc…</p> : error ? <p role="alert" className="text-sm text-danger">{error}</p> : !rows.length ? <p role="status">Không có lịch làm việc trong khoảng đã chọn.</p> : rows.map(row => <article key={row.shiftId} className="rounded-lg border border-border p-4 text-sm"><p className="font-semibold">{row.shiftDate} · {row.startTime}–{row.endTime}</p><p><ReferenceText kind="department" value={row.departmentId} /> · <ReferenceText kind="room" value={row.roomId} /></p><p>{row.status === "ACTIVE" ? "Đang hiệu lực" : "Đã hủy"}</p></article>)}</section>;
}
