"use client";

import Link from "next/link";
import { useSyncExternalStore } from "react";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { OrganizationDirectory } from "@/features/organization/components/OrganizationDirectory";
import { OrganizationAdminActions } from "@/features/organization/components/OrganizationAdminActions";
import { ProfileWorkspace } from "./profile/ProfileWorkspace";

export function OrganizationWorkspace() {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  if (role !== "ADMIN") return <PageShell title="Hồ sơ cá nhân" description="Thông tin của bạn và bảo mật tài khoản."><ProfileWorkspace /></PageShell>;
  return <PageShell title="Quản trị tổ chức" description="Quản lý khoa phòng, nhân sự và tài khoản trong bệnh viện.">
    <RoleGate allowed={["ADMIN"]}>
      <div className="mt-5"><Link href="/organization/profile" className="text-sm font-semibold text-primary hover:underline">Xem hồ sơ cá nhân →</Link></div>
      <OrganizationAdminActions />
      <OrganizationDirectory />
    </RoleGate>
  </PageShell>;
}
