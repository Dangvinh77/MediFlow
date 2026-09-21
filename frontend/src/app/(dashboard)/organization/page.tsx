import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DepartmentList } from "@/features/organization/components/DepartmentList";
import { StaffTable } from "@/features/organization/components/StaffTable";

export const metadata: Metadata = {
  title: "Tổ chức | MediFlow",
};

export default function OrganizationPage() {
  return (
    <PageShell
      title="Tổ chức"
      description="Khoa phòng và nhân sự trong bệnh viện."
    >
      <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE"]}>
        <DepartmentList />
        <StaffTable />
      </RoleGate>
    </PageShell>
  );
}
