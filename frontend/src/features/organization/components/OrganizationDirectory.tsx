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
  return <div className="space-y-5">
    <div role="group" aria-label="Chọn thông tin tổ chức" className="flex flex-wrap gap-1 border-b border-border pb-3">
      {sections.map(section => <button key={section.id} type="button" aria-pressed={selected === section.id}
        aria-controls="organization-directory-content" onClick={() => setSelected(section.id)}
        className={`min-h-11 rounded-lg px-5 py-2.5 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${selected === section.id ? "bg-surface-muted text-foreground" : "text-muted-foreground hover:bg-surface-muted hover:text-foreground"}`}>
        {section.label}
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
