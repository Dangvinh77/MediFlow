"use client";
import { useLocale } from "@/components/i18n/LocaleProvider";


import { useSyncExternalStore } from "react";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { OrganizationDirectory } from "@/features/organization/components/OrganizationDirectory";
import { OrganizationAdminActions } from "@/features/organization/components/OrganizationAdminActions";
import { ProfileWorkspace } from "./profile/ProfileWorkspace";

export function OrganizationWorkspace() {
  const { localize } = useLocale();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  if (role !== "ADMIN") return <PageShell title={localize("Hồ sơ cá nhân")} description={localize("Thông tin của bạn và bảo mật tài khoản.")}><ProfileWorkspace /></PageShell>;
  return <PageShell title={localize("Quản trị tổ chức")} description={localize("Quản lý khoa phòng, nhân sự và tài khoản trong bệnh viện.")} actions={<OrganizationAdminActions />}>
    <RoleGate allowed={["ADMIN"]}>
      <OrganizationDirectory />
    </RoleGate>
  </PageShell>;
}
