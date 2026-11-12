"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";

import { useEffect, useRef, useState } from "react";
import { getSessionKey } from "@/lib/session";
import { formatLocalDate } from "@/lib/format";
import { formatScheduleObservedAt } from "../schedule-presentation";
import { ScheduleDateInput } from "./ScheduleDateInput";
import { Button } from "@/components/ui/Button";
import { appointmentApi } from "../api";
import { shiftRange, vietnamToday } from "../session-dates";
import { DepartmentWeekBoard } from "./DepartmentWeekBoard";
import type { DepartmentSessionSummary } from "../types";
export function DepartmentOperationsSummary({ departmentId }: { departmentId: string }) {
  const { localize } = useLocale();
 const [view,setView] = useState<"day"|"week">("day"); const [date,setDate] = useState(vietnamToday);
 const [data,setData] = useState<DepartmentSessionSummary|null>(null); const [busy,setBusy] = useState(true); const [error,setError] = useState("");
 const [attempt,setAttempt] = useState(0); const reads=useRef(0);
 useEffect(() => {
  if (!date) return; const generation=++reads.current; const session=getSessionKey(); const range=shiftRange(date,view);
  Promise.resolve().then(() => { if(generation===reads.current){setBusy(true);setError("");setData(null);} });
  appointmentApi.sessionSummary(departmentId,range.fromDate,range.toDate).then(value => {if(generation===reads.current&&session===getSessionKey())setData(value);}).catch(cause=>{if(generation===reads.current&&session===getSessionKey())setError(cause instanceof Error?cause.message:"Không thể tải thống kê khoa.");}).finally(()=>{if(generation===reads.current&&session===getSessionKey())setBusy(false);});
  const currentReads=reads; return()=>{currentReads.current++;};
 },[departmentId,date,view,attempt]);
 return <div className="space-y-5"><section className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold"><L10n>{"Hoạt động khoa"}</L10n></h2><div className="flex flex-wrap items-end gap-4"><label className="text-sm font-medium"><span className="mb-1 block"><L10n>{"Khoảng thống kê"}</L10n></span><select value={view} onChange={event=>setView(event.target.value as "day"|"week")} className="block min-h-11 rounded-lg border border-control-border bg-surface px-3 font-normal"><option value="day"><L10n>{"Hôm nay / theo ngày"}</L10n></option><option value="week"><L10n>{"Theo tuần"}</L10n></option></select></label><ScheduleDateInput label={localize("Ngày thống kê")} required value={date} onChange={event=>setDate(event.target.value)} className="block min-h-11 rounded-lg border border-control-border bg-surface px-3"/></div><L10nValue>{busy?<p role="status"><L10n>{"Đang tải thống kê khoa…"}</L10n></p>:error?<div role="alert"><p className="text-sm text-danger"><L10nValue>{error}</L10nValue></p><Button onClick={()=>setAttempt(value=>value+1)}><L10n>{"Tải lại thống kê"}</L10n></Button></div>:data?<><dl className="grid grid-cols-2 gap-4 md:grid-cols-4"><L10nValue>{[["Buổi đang mở",data.openSessionCount],["Buổi ngừng nhận",data.stoppedSessionCount],["Sức chứa",data.capacity],["Còn trống",data.availableCount],["Giữ chỗ",data.heldCount],["Đang xử lý thu",data.paymentLockedCount],["Đã xác nhận",data.confirmedCount],["Đã phục vụ",data.consumedCount]].map(([label,value])=><div key={label} className="border-l-2 border-primary pl-3"><dt className="text-sm text-muted-foreground"><L10nValue>{label}</L10nValue></dt><dd className="mt-1 text-2xl font-semibold">{value}</dd></div>)}</L10nValue></dl><p className="text-xs text-muted-foreground"><L10n>{"Toàn khoa · "}</L10n><L10nValue>{formatLocalDate(data.fromDate)}</L10nValue>–<L10nValue>{formatLocalDate(data.toDate)}</L10nValue><L10n>{" · Cập nhật: "}</L10n><L10nValue>{formatScheduleObservedAt(data.observedAt)}</L10nValue><L10n>{". Số giữ chỗ và xác nhận không phải số người đang đợi khám."}</L10n></p></>:null}</L10nValue></section><L10nValue>{date?<DepartmentWeekBoard departmentId={departmentId} date={date}/>:null}</L10nValue></div>;
}
