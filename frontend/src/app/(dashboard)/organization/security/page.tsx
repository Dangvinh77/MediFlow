"use client";

import { useSyncExternalStore } from "react";
import { PageShell } from "@/components/layout/PageShell";
import { RoleGate } from "@/components/auth/RoleGate";
import { mustChangePassword, subscribeToAuthChanges } from "@/lib/session";
import { PasswordForm } from "@/features/organization/components/PasswordForm";
export default function Page() {
  const required = useSyncExternalStore(subscribeToAuthChanges, mustChangePassword, () => false);
  const form = <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT"]}><PasswordForm /></RoleGate>;
  return required ? form : <PageShell title="Bảo mật tài khoản" description="Quản lý mật khẩu đăng nhập của bạn.">{form}</PageShell>;
}
