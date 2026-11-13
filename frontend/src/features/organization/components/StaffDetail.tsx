"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { DetailSection, RecordIdentity } from "./RecordLayout";
import { StaffDepartment } from "./StaffDepartment";
import { ProfileAccountPanel } from "./ProfileAccountPanel";
import { StaffCapabilityGrants } from "./StaffCapabilityGrants";
import { DeactivateStaffAction } from "./DeactivateStaffAction";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import type { JobTitle, StaffDTO } from "../types";

const getServerRole = () => null;
const jobs: Record<JobTitle, string> = { DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", TECHNICIAN: "Kỹ thuật viên", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", ADMINISTRATIVE: "Hành chính" };

export function StaffDetail({ staffId, notice }: { staffId: string; notice?: "created" | "updated" }) {
  return <StaffDetailWorkspace key={staffId} staffId={staffId} notice={notice} />;
}

function StaffDetailWorkspace({ staffId, notice }: { staffId: string; notice?: "created" | "updated" }) {
  const { localize } = useLocale();
  const router = useRouter(); const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole); const [staff, setStaff] = useState<StaffDTO | null>(null); const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [deactivated, setDeactivated] = useState(false);
  const readGeneration = useRef(0);
  useEffect(() => { let mounted = true; const generation = ++readGeneration.current; const sessionToken = getSessionKey(); organizationApi.staffMember(staffId).then((value) => { if (mounted && generation === readGeneration.current && isRequestSessionCurrent(sessionToken)) setStaff(value); }).catch((cause: unknown) => { if (!mounted || generation !== readGeneration.current || !isRequestSessionCurrent(sessionToken, cause)) return; if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể tải nhân sự."); }); return () => { mounted = false; }; }, [router, staffId, attempt]);
  if (error) return <AsyncState kind="error" message={localize(error)} onRetry={() => { setError(null); setAttempt((value) => value + 1); }} />; if (!staff) return <AsyncState kind="loading" message={localize("Đang tải nhân sự…")} />;
  return <section className="mt-6 space-y-5">
    {deactivated ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success"><L10n>Đã ngừng làm việc nhân viên và khóa tài khoản liên kết.</L10n></p> : null}
    <L10nValue>{notice ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success"><L10nValue>{notice === "created" ? "Đã tạo nhân sự." : "Đã cập nhật nhân sự."}</L10nValue></p> : null}</L10nValue>
    <RecordIdentity name={staff.fullName} subtitle={jobs[staff.jobTitle]}><StatusBadge tone={staff.active ? "success" : "neutral"}><L10nValue>{staff.active ? "Đang làm việc" : "Đã ngừng"}</L10nValue></StatusBadge><L10nValue>{role === "ADMIN" ? <Link href={`/organization/staff/${staffId}/edit`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"><L10n>{"Chỉnh sửa"}</L10n></Link> : null}</L10nValue></RecordIdentity>
    <div className="flex flex-wrap gap-3"><Link href="/organization" className="inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary"><L10n>{"Quay lại"}</L10n></Link><DeactivateStaffAction staff={staff} onChanged={value => { readGeneration.current += 1; setError(null); setStaff(value); setDeactivated(true); }} /></div>
    <div className="grid items-start gap-5 xl:grid-cols-2">
      <DetailSection title={localize("Thông tin công tác")}><Info label={localize("Chức danh")} value={jobs[staff.jobTitle]} /><Info label={localize("Chuyên môn")} value={staff.specialization} /><Info label={localize("Trình độ chuyên môn")} value={staff.qualification} /><L10nValue>{role === "ADMIN" ? <><Info label={localize("Giấy phép hành nghề")} value={staff.licenseNumber} /><Info label={localize("Ngày vào làm")} value={date(staff.hireDate)} /></> : null}</L10nValue><StaffDepartment key={staff.departmentId} departmentId={staff.departmentId} /></DetailSection>
      <L10nValue>{role === "ADMIN" ? <DetailSection title={localize("Thông tin cá nhân và liên hệ")}><Info label={localize("CCCD")} value={staff.identityNumber} /><Info label={localize("Ngày sinh")} value={date(staff.dateOfBirth)} /><Info label={localize("Giới tính")} value={staff.gender ? { MALE: "Nam", FEMALE: "Nữ", OTHER: "Khác" }[staff.gender] : null} /><Info label={localize("Địa chỉ")} value={staff.address} /><Info label={localize("Điện thoại")} value={staff.phoneNumber} /><Info label={localize("Email")} value={staff.email} /></DetailSection> : null}</L10nValue>
    </div>
    <L10nValue>{role === "ADMIN" ? <ProfileAccountPanel key={`${staff.staffId}:${staff.active}`} ownerType="staff" ownerId={staff.staffId} profileActive={staff.active} jobTitle={staff.jobTitle} onProfileChanged={() => setAttempt(value => value + 1)} /> : null}</L10nValue>
    <L10nValue>{role === "ADMIN" ? <StaffCapabilityGrants staffId={staff.staffId} /> : null}</L10nValue>
  </section>;
}
function Info({ label, value, mono = false }: { label: string; value: string | null | undefined; mono?: boolean }) { return <div><dt className="text-sm text-muted-foreground"><L10nValue>{label}</L10nValue></dt><dd className={`mt-1.5 break-words text-sm leading-6 ${mono ? "font-mono" : ""}`}><L10nValue>{value ?? "—"}</L10nValue></dd></div>; }

function date(value: string | null) { return value ? value.split("-").reverse().join("/") : null; }
