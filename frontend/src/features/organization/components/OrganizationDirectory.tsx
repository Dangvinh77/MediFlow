"use client";
import { useState } from "react";
import { DepartmentList } from "./DepartmentList";
import { StaffTable } from "./StaffTable";
import type { DepartmentDTO } from "../types";
export function OrganizationDirectory() {
  const [departments, setDepartments] = useState<DepartmentDTO[]>([]);
  return <><DepartmentList onLoaded={setDepartments} /><StaffTable departments={departments} /></>;
}
