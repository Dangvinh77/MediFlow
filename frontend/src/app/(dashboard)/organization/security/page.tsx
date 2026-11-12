"use client";
import { L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useSyncExternalStore } from "react";
import { PageShell } from "@/components/layout/PageShell";
import { RoleGate } from "@/components/auth/RoleGate";
import { mustChangePassword, subscribeToAuthChanges } from "@/lib/session";
import { PasswordForm } from "@/features/organization/components/PasswordForm";
export default function Page() {
  const { localize } = useLocale();
  const required = useSyncExternalStore(subscribeToAuthChanges, mustChangePassword, () => false);
  const form = <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT"]}><PasswordForm /></RoleGate>;
  return required ? form : <PageShell title={localize("Bảo mật tài khoản")} description={localize("Quản lý mật khẩu đăng nhập của bạn.")}><L10nValue>{form}</L10nValue></PageShell>;
}
