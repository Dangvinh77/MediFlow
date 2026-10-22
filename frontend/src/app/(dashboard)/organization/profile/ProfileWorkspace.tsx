"use client";

import Link from "next/link";
import { useSyncExternalStore } from "react";
import { RoleGate } from "@/components/auth/RoleGate";
import { AsyncState } from "@/components/ui/AsyncState";
import { getRole, getToken, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { readProfileIdentity } from "@/features/organization/profile-identity";
import { PersonalStaffProfile } from "@/features/organization/components/PersonalStaffProfile";
import { PersonalPatientProfile } from "@/features/patient/components/PersonalPatientProfile";
import { DetailSection } from "@/features/organization/components/RecordLayout";
import type { Role } from "@/lib/roles";

const serverSnapshot = () => null;
const labels: Partial<Record<Role, string>> = { ADMIN: "Quản trị viên", MANAGER: "Quản lý", DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", LAB_TECH: "Kỹ thuật viên xét nghiệm", PATIENT: "Bệnh nhân" };
const linkStyle = "inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary";

export function ProfileWorkspace() {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, serverSnapshot);
  const token = useSyncExternalStore(subscribeToAuthChanges, getToken, serverSnapshot);
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, serverSnapshot);
  const identity = readProfileIdentity(token, role);
  return <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT"]}>
    <section className="mt-6 space-y-5">
      <div className="flex flex-wrap gap-3">
        <Link href="/organization/security" className={linkStyle}>Đổi mật khẩu</Link>
        {role === "ADMIN" ? <Link href="/organization" className={linkStyle}>Quản trị tổ chức</Link> : null}
        {role && ["MANAGER", "DOCTOR", "NURSE"].includes(role) ? <Link href="/organization/directory" className={linkStyle}>Tra cứu khoa và nhân sự</Link> : null}
      </div>
      <DetailSection title="Tài khoản của bạn">
        <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Vai trò</dt><dd className="mt-1 text-sm">{role ? labels[role] : "—"}</dd></div>
        <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Loại hồ sơ</dt><dd className="mt-1 text-sm">{role === "PATIENT" ? "Bệnh nhân" : "Nhân viên"}</dd></div>
      </DetailSection>
      {identity ? <details className="rounded-xl border border-border bg-surface p-5"><summary className="cursor-pointer text-sm font-semibold">Định danh tài khoản</summary><p className="mt-3 break-words text-sm">Mã tài khoản: {identity.accountId}</p></details> : null}
      {!identity ? <AsyncState kind="empty" message="Không đọc được định danh tài khoản. Vui lòng đăng nhập lại." />
        : !identity.profileId ? <AsyncState kind="empty" message="Tài khoản chưa được liên kết với hồ sơ nhân sự. Vui lòng liên hệ quản trị viên." />
        : role === "PATIENT" ? <PersonalPatientProfile key={`${session}:${identity.profileId}`} patientId={identity.profileId} />
        : <PersonalStaffProfile key={`${session}:${identity.profileId}`} staffId={identity.profileId} />}
    </section>
  </RoleGate>;
}
