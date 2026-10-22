"use client";
import { useCallback } from "react";
import { RecordPicker } from "./RecordPicker";
import { organizationApi } from "../api";
import type { JobTitle } from "../types";
export function StaffPicker({ value, onChange, departmentId, jobTitle, disabled = false, id = "doctorId", label = "Bác sĩ", required = false }: {
  value: string; onChange: (id: string) => void; departmentId?: string; jobTitle?: JobTitle; disabled?: boolean; id?: string; label?: string; required?: boolean;
}) {
  const load = useCallback(async (_keyword: string, page: number) => {
    const result = await organizationApi.staff({ departmentId, jobTitle, page, size: 20 });
    return { items: result.content.filter(staff => staff.active).map(staff => ({ id: staff.staffId, label: `${staff.fullName}${staff.specialization ? ` · ${staff.specialization}` : ""}` })), totalPages: result.totalPages };
  }, [departmentId, jobTitle]);
  return <RecordPicker key={`${departmentId ?? "all"}:${jobTitle ?? "all"}`} id={id} label={label} value={value} onChange={onChange} disabled={disabled} load={load} searchable={false} required={required} />;
}
