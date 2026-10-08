import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DepartmentList } from "@/features/organization/components/DepartmentList";
import { StaffTable } from "@/features/organization/components/StaffTable";
import { OrganizationAdminActions } from "@/features/organization/components/OrganizationAdminActions";

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
        <OrganizationAdminActions />
        <DepartmentList />
        <StaffTable />
      </RoleGate>
    </PageShell>
  );
}
