"use client";
import { useState } from "react";
import { useCapabilities } from "@/lib/capabilities";
import { DepartmentOperationsSummary } from "./DepartmentOperationsSummary";
import { SessionAdministration } from "./ScheduleManagementWorkspace";
import { DoctorWorkShiftsWorkspace } from "./DoctorWorkShiftsWorkspace";

export function DepartmentManagementWorkspace({ enabled, shifts = false, sessions = false, initialDate }: { enabled: boolean; shifts?: boolean; sessions?: boolean; initialDate?: string }) {
  const capabilities = useCapabilities();
  if (!enabled) return <p role="status">Quản lý lịch chưa được bật.</p>;
  if (!capabilities.data && !capabilities.error) return <p role="status">Đang kiểm tra bổ nhiệm hiện hành…</p>;
  if (capabilities.error) return <p role="alert">{capabilities.error}</p>;
  const departments = capabilities.data?.role === "DOCTOR" ? capabilities.data.managementDepartments : [];
  if (!departments.length) return <p role="alert">Tài khoản không có bổ nhiệm trưởng khoa đang hiệu lực.</p>;
  return <DepartmentScope key={departments.map(item => `${item.appointmentId}:${item.revision}`).join()} departments={departments} shifts={shifts} sessions={sessions} initialDate={initialDate} checking={capabilities.loading} />;
}
function DepartmentScope({ departments, shifts, sessions, checking, initialDate }: { departments: { departmentId: string; departmentName: string }[]; shifts: boolean; sessions: boolean; checking: boolean; initialDate?: string }) {
  const [departmentId, setDepartmentId] = useState(departments[0].departmentId);
  return <div className="space-y-5"><label className="block max-w-lg space-y-1 text-sm font-medium">Khoa được bổ nhiệm<select aria-label="Khoa được bổ nhiệm" value={departmentId} onChange={event => setDepartmentId(event.target.value)} disabled={checking} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3">{departments.map(item => <option key={item.departmentId} value={item.departmentId}>{item.departmentName}</option>)}</select></label>{checking ? <p role="status" className="text-sm text-muted-foreground">Đang kiểm tra lại quyền hiện hành…</p> : null}{shifts ? <DoctorWorkShiftsWorkspace key={departmentId} departmentId={departmentId} checking={checking} /> : <>{!sessions ? <DepartmentOperationsSummary key={`summary:${departmentId}`} departmentId={departmentId}/> : null}<SessionAdministration key={`${departmentId}:${initialDate ?? ""}`} fixedDepartmentId={departmentId} initialDate={initialDate} checking={checking} /></>}</div>;
}
