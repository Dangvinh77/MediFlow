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
function normalizeSearch(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/đ/g, "d").replace(/Đ/g, "D").toLowerCase().trim();
}
export function StaffPicker({ value, onChange, departmentId, jobTitle, disabled = false, id = "doctorId", label = "Bác sĩ", required = false }: {
  value: string; onChange: (id: string) => void; departmentId?: string; jobTitle?: JobTitle; disabled?: boolean; id?: string; label?: string; required?: boolean;
}) {
  const load = useCallback(async (keyword: string, page: number) => {
    const session = getSessionKey();
    const query = normalizeSearch(keyword);
    const [result, departments] = await Promise.all([
      organizationApi.staff({ departmentId, jobTitle, page: query ? 0 : page, size: query ? 100 : 20 }),
      organizationApi.departments(false),
    ]);
    const staffMembers = [...result.content];
    if (query) {
      for (let nextPage = 1; nextPage < result.totalPages; nextPage++) {
        if (getSessionKey() !== session) throw new Error("Phiên đăng nhập đã thay đổi.");
        const next = await organizationApi.staff({ departmentId, jobTitle, page: nextPage, size: 100 });
        staffMembers.push(...next.content);
      }
    }
    const departmentNames = new Map(departments.map(department => [department.departmentId, department.departmentName]));
    const items = staffMembers.filter(staff => staff.active).map(staff => ({
      id: staff.staffId,
      label: `${staff.fullName} · ${jobTitleLabels[staff.jobTitle]} · ${departmentNames.get(staff.departmentId) ?? "Chưa có thông tin khoa/phòng"}`,
    })).filter(item => !query || normalizeSearch(item.label).includes(query));
    return {
      items: query ? items.slice(page * 20, (page + 1) * 20) : items,
      totalPages: query ? Math.ceil(items.length / 20) : result.totalPages,
    };
  }, [departmentId, jobTitle]);
  return <RecordPicker key={`${departmentId ?? "all"}:${jobTitle ?? "all"}`} id={id} label={label} value={value} onChange={onChange} disabled={disabled} load={load} required={required} />;
}
