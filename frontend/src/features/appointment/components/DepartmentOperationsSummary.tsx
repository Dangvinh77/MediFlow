"use client";
import { useEffect, useRef, useState } from "react";
import { getSessionKey } from "@/lib/session";
import { formatLocalDate } from "@/lib/format";
import { formatScheduleObservedAt } from "../schedule-presentation";
import { ScheduleDateInput } from "./ScheduleDateInput";
import { Button } from "@/components/ui/Button";
import { appointmentApi } from "../api";
import { vietnamToday } from "../session-dates";
import { shiftRange } from "./MyWorkShiftsWorkspace";
import type { DepartmentSessionSummary } from "../types";
export function DepartmentOperationsSummary({ departmentId }: { departmentId: string }) {
 const [view,setView] = useState<"day"|"week">("day"); const [date,setDate] = useState(vietnamToday);
 const [data,setData] = useState<DepartmentSessionSummary|null>(null); const [busy,setBusy] = useState(true); const [error,setError] = useState("");
 const [attempt,setAttempt] = useState(0); const reads=useRef(0);
 useEffect(() => {
  if (!date) return; const generation=++reads.current; const session=getSessionKey(); const range=shiftRange(date,view);
  Promise.resolve().then(() => { if(generation===reads.current){setBusy(true);setError("");setData(null);} });
  appointmentApi.sessionSummary(departmentId,range.fromDate,range.toDate).then(value => {if(generation===reads.current&&session===getSessionKey())setData(value);}).catch(cause=>{if(generation===reads.current&&session===getSessionKey())setError(cause instanceof Error?cause.message:"Không thể tải thống kê khoa.");}).finally(()=>{if(generation===reads.current&&session===getSessionKey())setBusy(false);});
  const currentReads=reads; return()=>{currentReads.current++;};
 },[departmentId,date,view,attempt]);
 return <section className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold">Hoạt động khoa</h2><div className="flex flex-wrap gap-4"><label className="text-sm">Khoảng thống kê<select value={view} onChange={event=>setView(event.target.value as "day"|"week")} className="block min-h-11 rounded-lg border border-control-border bg-surface px-3"><option value="day">Hôm nay / theo ngày</option><option value="week">Theo tuần</option></select></label><ScheduleDateInput label="Ngày thống kê" required value={date} onChange={event=>setDate(event.target.value)} className="block min-h-11 rounded-lg border border-control-border bg-surface px-3"/></div>{busy?<p role="status">Đang tải thống kê khoa…</p>:error?<div role="alert"><p className="text-sm text-danger">{error}</p><Button onClick={()=>setAttempt(value=>value+1)}>Tải lại thống kê</Button></div>:data?<><dl className="grid grid-cols-2 gap-4 md:grid-cols-4">{[["Buổi đang mở",data.openSessionCount],["Buổi ngừng nhận",data.stoppedSessionCount],["Sức chứa",data.capacity],["Còn trống",data.availableCount],["Giữ chỗ",data.heldCount],["Đang xử lý thu",data.paymentLockedCount],["Đã xác nhận",data.confirmedCount],["Đã phục vụ",data.consumedCount]].map(([label,value])=><div key={label} className="border-l-2 border-primary pl-3"><dt className="text-sm text-muted-foreground">{label}</dt><dd className="mt-1 text-2xl font-semibold">{value}</dd></div>)}</dl><p className="text-xs text-muted-foreground">Toàn khoa · {formatLocalDate(data.fromDate)}–{formatLocalDate(data.toDate)} · Cập nhật: {formatScheduleObservedAt(data.observedAt)}. Số giữ chỗ và xác nhận không phải số người đang đợi khám.</p></>:null}</section>;
}
