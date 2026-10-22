import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { OrganizationDirectory } from "@/features/organization/components/OrganizationDirectory";

export const metadata: Metadata = { title: "Danh bạ tổ chức | MediFlow" };

export default function DirectoryPage() {
  return <PageShell title="Danh bạ tổ chức" description="Tra cứu khoa phòng và thông tin công tác của nhân sự.">
    <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE"]}><OrganizationDirectory /></RoleGate>
  </PageShell>;
}
