"use client";
import { useCallback } from "react";
import { getSessionKey } from "@/lib/session";
import { RecordPicker } from "./RecordPicker";
import { organizationApi } from "../api";
import type { JobTitle } from "../types";
const jobTitleLabels: Record<JobTitle, string> = {
  DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", TECHNICIAN: "Kỹ thuật viên",
  PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", MANAGER: "Quản lý",
  ADMINISTRATIVE: "Hành chính",
};
export function StaffPicker({ value, onChange, departmentId, jobTitle, disabled = false, id = "doctorId", label = "Bác sĩ", required = false }: {
  value: string; onChange: (id: string) => void; departmentId?: string; jobTitle?: JobTitle; disabled?: boolean; id?: string; label?: string; required?: boolean;
}) {
  const load = useCallback(async (keyword: string, page: number) => {
    const session = getSessionKey();
    const [result, departments] = await Promise.all([
      organizationApi.staff({ departmentId, jobTitle, keyword: keyword.trim() || undefined, activeOnly: true, page, size: 20 }),
      organizationApi.departments(false),
    ]);
    if (getSessionKey() !== session) throw new Error("Phiên đăng nhập đã thay đổi.");
    const departmentNames = new Map(departments.map(department => [department.departmentId, department.departmentName]));
    const items = result.content.filter(staff => staff.active).map(staff => ({
      id: staff.staffId,
      label: `${staff.fullName} · ${jobTitleLabels[staff.jobTitle]} · ${departmentNames.get(staff.departmentId) ?? "Chưa có thông tin khoa/phòng"}`,
    }));
    return {
      items,
      totalPages: result.totalPages,
    };
  }, [departmentId, jobTitle]);
  return <RecordPicker key={`${departmentId ?? "all"}:${jobTitle ?? "all"}`} id={id} label={label} value={value} onChange={onChange} disabled={disabled} load={load} required={required} />;
}
