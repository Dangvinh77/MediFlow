import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { OrganizationDirectory } from "@/features/organization/components/OrganizationDirectory";
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
        <OrganizationDirectory />
      </RoleGate>
    </PageShell>
  );
}
