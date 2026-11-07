"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ReferenceText } from "@/components/references/ReferenceText";
import { formatLocalDate } from "@/lib/format";
import { getSessionKey } from "@/lib/session";
import { appointmentApi } from "../api";
import { sessionStatusLabels } from "../presentation";
import { shiftRange } from "../session-dates";
import type { ConsultationSessionDTO } from "../types";

const weekdays = ["Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"];

/** Read-only week of the whole department; changes happen on the sessions and shifts screens. */
export function DepartmentWeekBoard({ departmentId, date }: { departmentId: string; date: string }) {
  const { fromDate, toDate } = shiftRange(date, "week");
  const [rows, setRows] = useState<ConsultationSessionDTO[] | null>(null);
  const [error, setError] = useState("");
  const reads = useRef(0);
  useEffect(() => {
    const generation = ++reads.current; const session = getSessionKey();
    const current = () => generation === reads.current && session === getSessionKey();
    (async () => {
      const all: ConsultationSessionDTO[] = []; let page = 0; let pages = 1;
      while (page < pages) {
        const value = await appointmentApi.listConsultationSessions({ departmentId, fromDate, toDate, page, size: 50 });
        all.push(...value.content); pages = value.totalPages; page++;
      }
      return all;
    })().then(value => { if (current()) { setRows(value); setError(""); } })
      .catch(cause => { if (current()) { setRows(null); setError(cause instanceof Error ? cause.message : "Không thể tải lịch toàn khoa."); } });
    const currentReads = reads; return () => { currentReads.current++; };
  }, [departmentId, fromDate, toDate]);
  const days = weekdays.map((label, index) => {
    const value = new Date(`${fromDate}T12:00:00Z`); value.setUTCDate(value.getUTCDate() + index);
    return { label, date: value.toISOString().slice(0, 10) };
  });
  return <section className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <div><h2 className="text-lg font-semibold">Lịch toàn khoa trong tuần</h2><p className="mt-1 text-sm text-muted-foreground">Ai khám, ở phòng nào và còn bao nhiêu chỗ. Bấm một ngày để lập hoặc sửa buổi khám.</p></div>
    {error ? <p role="alert" className="text-sm text-danger">{error}</p> : !rows ? <p role="status" className="text-sm text-muted-foreground">Đang tải lịch toàn khoa…</p> :
    <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="Lịch toàn khoa theo tuần"><div className="grid min-w-[70rem] grid-cols-7 gap-2">
      {days.map(day => {
        const sessions = rows.filter(row => row.sessionDate === day.date).sort((a, b) => a.startTime.localeCompare(b.startTime));
        return <div key={day.date} className="min-w-0 space-y-2 rounded-lg border border-border p-2">
          <Link href={`/appointments/department/sessions?date=${day.date}`} className="block rounded-md px-1 py-1 text-sm font-semibold hover:text-primary">{day.label}<span className="block text-xs font-normal text-muted-foreground">{formatLocalDate(day.date)}</span></Link>
          {sessions.length ? sessions.map(row => <article key={row.sessionId} className="space-y-1 rounded-md bg-surface-muted p-2 text-xs">
            <p className="font-semibold">{row.startTime}–{row.endTime}</p>
            <p><ReferenceText kind="doctor" value={row.doctorId} fallback="Bác sĩ" /></p>
            <p className="text-muted-foreground"><ReferenceText kind="room" value={row.roomId} fallback="Phòng" /></p>
            <p>{row.confirmedCount}/{row.capacity} đã xác nhận · {sessionStatusLabels[row.status]}</p>
          </article>) : <p className="px-1 text-xs text-muted-foreground">Chưa có buổi</p>}
        </div>;
      })}
    </div></div>}
  </section>;
}
