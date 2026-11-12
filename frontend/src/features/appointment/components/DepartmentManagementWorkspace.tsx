"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";

import { useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { useCapabilities } from "@/lib/capabilities";
import { DepartmentOperationsSummary } from "./DepartmentOperationsSummary";
import { SessionAdministration } from "./ScheduleManagementWorkspace";
import { DoctorWorkShiftsWorkspace } from "./DoctorWorkShiftsWorkspace";

export function DepartmentManagementWorkspace({ enabled, shifts = false, sessions = false, initialDate, clinicIntakeEnabled = false }: { enabled: boolean; shifts?: boolean; sessions?: boolean; initialDate?: string; clinicIntakeEnabled?: boolean }) {
  const { localize } = useLocale();
  const capabilities = useCapabilities();
  if (!enabled) return <p role="status"><L10n>{"Quản lý lịch chưa được bật."}</L10n></p>;
  if (!capabilities.data && !capabilities.error) return <AsyncState kind="loading" message={localize("Đang kiểm tra bổ nhiệm hiện hành…")} />;
  if (capabilities.error) return <p role="alert"><L10nValue>{capabilities.error}</L10nValue></p>;
  const departments = capabilities.data?.role === "DOCTOR" ? capabilities.data.managementDepartments : [];
  if (!departments.length) return <p role="alert"><L10n>{"Tài khoản không có bổ nhiệm trưởng khoa đang hiệu lực."}</L10n></p>;
  return <DepartmentScope clinicIntakeEnabled={clinicIntakeEnabled} key={departments.map(item => `${item.appointmentId}:${item.revision}`).join()} departments={departments} shifts={shifts} sessions={sessions} initialDate={initialDate} checking={capabilities.loading} />;
}
function DepartmentScope({ departments, shifts, sessions, checking, initialDate, clinicIntakeEnabled }: { departments: { departmentId: string; departmentName: string }[]; shifts: boolean; sessions: boolean; checking: boolean; initialDate?: string; clinicIntakeEnabled: boolean }) {
  const { localize } = useLocale();
  const [departmentId, setDepartmentId] = useState(departments[0].departmentId);
  return <div className="space-y-5"><label className="block max-w-lg space-y-1 text-sm font-medium"><L10n>{"Khoa được bổ nhiệm"}</L10n><select aria-label={localize("Khoa được bổ nhiệm")} value={departmentId} onChange={event => setDepartmentId(event.target.value)} disabled={checking} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3"><L10nValue>{departments.map(item => <option key={item.departmentId} value={item.departmentId}>{item.departmentName}</option>)}</L10nValue></select></label><L10nValue>{checking ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Đang kiểm tra lại quyền hiện hành…"}</L10n></p> : null}</L10nValue><L10nValue>{shifts ? <DoctorWorkShiftsWorkspace key={departmentId} departmentId={departmentId} checking={checking} clinicIntakeEnabled={clinicIntakeEnabled} /> : <><L10nValue>{sessions ? <SessionAdministration key={`${departmentId}:${initialDate ?? ""}`} fixedDepartmentId={departmentId} initialDate={initialDate} checking={checking} clinicIntakeEnabled={clinicIntakeEnabled} /> : <DepartmentOperationsSummary key={`summary:${departmentId}`} departmentId={departmentId}/>}</L10nValue></>}</L10nValue></div>;
}
