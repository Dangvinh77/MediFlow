"use client";
import { L10nValue, useLocale } from "@/components/i18n/LocaleProvider";

import { useState } from "react";
import { DepartmentList } from "./DepartmentList";
import { RoomDirectory } from "./RoomDirectory";
import { RoomTypeCatalog } from "./RoomTypeCatalog";
import { StaffTable } from "./StaffTable";
import type { DepartmentDTO } from "../types";
const sections = [
  { id: "departments", label: "Khoa / bộ phận", description: "Khoa lâm sàng, cận lâm sàng và bộ phận chức năng" },
  { id: "rooms", label: "Phòng làm việc", description: "Phòng khám, văn phòng khoa và nơi làm việc của bộ phận" },
  { id: "staff", label: "Nhân sự", description: "Hồ sơ và thông tin công tác" },
  { id: "catalog", label: "Danh mục khoa phòng", description: "Khối khoa và loại phòng được phép trong từng khối" },
] as const;
export function OrganizationDirectory() {
  const { localize } = useLocale();
  const [departments, setDepartments] = useState<DepartmentDTO[]>([]);
  const [selected, setSelected] = useState<typeof sections[number]["id"]>("departments");
  return <div className="space-y-5">
    <div role="group" aria-label={localize("Chọn thông tin tổ chức")} className="flex flex-wrap gap-1 border-b border-border pb-3">
      <L10nValue>{sections.map(section => <button key={section.id} type="button" aria-pressed={selected === section.id}
        aria-controls="organization-directory-content" onClick={() => setSelected(section.id)}
        className={`min-h-11 rounded-lg px-5 py-2.5 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${selected === section.id ? "bg-surface-muted text-foreground" : "text-muted-foreground hover:bg-surface-muted hover:text-foreground"}`}>
        <L10nValue>{section.label}</L10nValue>
      </button>)}</L10nValue>
    </div>
    <div id="organization-directory-content">
      <div hidden={selected !== "departments"}>
        <DepartmentList category="all" onLoaded={setDepartments} />
      </div>
      <L10nValue>{selected === "rooms" ? <RoomDirectory /> : null}</L10nValue>
      <L10nValue>{selected === "staff" ? <StaffTable departments={departments} /> : null}</L10nValue>
      <L10nValue>{selected === "catalog" ? <RoomTypeCatalog /> : null}</L10nValue>
    </div>
  </div>;
}
