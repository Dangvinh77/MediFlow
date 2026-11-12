"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


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
const labels: Partial<Record<Role, string>> = { ADMIN: "Quản trị viên", DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", LAB_TECH: "Kỹ thuật viên xét nghiệm", PATIENT: "Bệnh nhân" };
const linkStyle = "inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary";

export function ProfileWorkspace() {
  const { localize } = useLocale();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, serverSnapshot);
  const token = useSyncExternalStore(subscribeToAuthChanges, getToken, serverSnapshot);
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, serverSnapshot);
  const identity = readProfileIdentity(token, role);
  return <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT"]}>
    <section className="mt-6 space-y-5">
      <div className="flex flex-wrap gap-3">
        <Link href="/organization/security" className={linkStyle}><L10n>{"Đổi mật khẩu"}</L10n></Link>
        <L10nValue>{role === "ADMIN" ? <Link href="/organization" className={linkStyle}><L10n>{"Quản trị tổ chức"}</L10n></Link> : null}</L10nValue>
        <L10nValue>{role && ["DOCTOR", "NURSE"].includes(role) ? <Link href="/organization/directory" className={linkStyle}><L10n>{"Tra cứu khoa và nhân sự"}</L10n></Link> : null}</L10nValue>
      </div>
      <DetailSection title={localize("Tài khoản của bạn")}>
        <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground"><L10n>{"Vai trò"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{role ? labels[role] : "—"}</L10nValue></dd></div>
        <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground"><L10n>{"Loại hồ sơ"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{role === "PATIENT" ? "Bệnh nhân" : "Nhân viên"}</L10nValue></dd></div>
      </DetailSection>
      <L10nValue>{!identity ? <AsyncState kind="empty" message={localize("Không đọc được định danh tài khoản. Vui lòng đăng nhập lại.")} />
        : !identity.profileId ? <AsyncState kind="empty" message={localize("Tài khoản chưa được liên kết với hồ sơ nhân sự. Vui lòng liên hệ quản trị viên.")} />
        : role === "PATIENT" ? <PersonalPatientProfile key={`${session}:${identity.profileId}`} patientId={identity.profileId} />
        : <PersonalStaffProfile key={`${session}:${identity.profileId}`} staffId={identity.profileId} />}</L10nValue>
    </section>
  </RoleGate>;
}
