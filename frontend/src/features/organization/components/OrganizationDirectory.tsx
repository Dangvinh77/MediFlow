"use client";
import { useState } from "react";
import { DepartmentList } from "./DepartmentList";
import { StaffTable } from "./StaffTable";
import type { DepartmentDTO } from "../types";
const sections = [
  { id: "departments", label: "Khoa", description: "Lâm sàng và cận lâm sàng" },
  { id: "offices", label: "Phòng", description: "Các phòng hành chính" },
  { id: "staff", label: "Nhân sự", description: "Hồ sơ và thông tin công tác" },
] as const;
export function OrganizationDirectory() {
  const [departments, setDepartments] = useState<DepartmentDTO[]>([]);
  const [selected, setSelected] = useState<typeof sections[number]["id"]>("departments");
  return <div className="mt-6 space-y-5">
    <div role="group" aria-label="Chọn thông tin tổ chức" className="grid gap-3 sm:grid-cols-3">
      {sections.map(section => <button key={section.id} type="button" aria-pressed={selected === section.id}
        aria-controls="organization-directory-content" onClick={() => setSelected(section.id)}
        className={`min-h-24 rounded-xl border p-4 text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${selected === section.id ? "border-primary bg-primary/10" : "border-border bg-surface hover:bg-surface-muted"}`}>
        <span className={`block text-base font-semibold ${selected === section.id ? "text-primary" : "text-foreground"}`}>{section.label}</span>
        <span className="mt-1 block text-sm text-muted-foreground">{section.description}</span>
      </button>)}
    </div>
    <div id="organization-directory-content">
      <div hidden={selected === "staff"}>
        <DepartmentList category={selected === "offices" ? "offices" : "departments"} onLoaded={setDepartments} />
      </div>
      {selected === "staff" ? <StaffTable departments={departments} /> : null}
    </div>
  </div>;
}
