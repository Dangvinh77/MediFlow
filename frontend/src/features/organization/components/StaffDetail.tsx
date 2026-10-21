"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, useSyncExternalStore } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { formatInstant } from "@/lib/format";
import { DetailSection, RecordIdentity } from "./RecordLayout";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import type { JobTitle, StaffDTO } from "../types";

const getServerRole = () => null;
const jobs: Record<JobTitle, string> = { DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", TECHNICIAN: "Kỹ thuật viên", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", MANAGER: "Quản lý", ADMINISTRATIVE: "Hành chính" };

export function StaffDetail({ staffId, notice }: { staffId: string; notice?: "created" | "updated" }) {
  return <StaffDetailWorkspace key={staffId} staffId={staffId} notice={notice} />;
}

function StaffDetailWorkspace({ staffId, notice }: { staffId: string; notice?: "created" | "updated" }) {
  const router = useRouter(); const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole); const [staff, setStaff] = useState<StaffDTO | null>(null); const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => { let mounted = true; const sessionToken = getSessionKey(); organizationApi.staffMember(staffId).then((value) => { if (mounted && isRequestSessionCurrent(sessionToken)) setStaff(value); }).catch((cause: unknown) => { if (!mounted || !isRequestSessionCurrent(sessionToken, cause)) return; if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể tải nhân sự."); }); return () => { mounted = false; }; }, [router, staffId, attempt]);
  if (error) return <AsyncState kind="error" message={error} onRetry={() => { setError(null); setAttempt((value) => value + 1); }} />; if (!staff) return <AsyncState kind="loading" message="Đang tải nhân sự…" />;
  return <section className="mt-6 space-y-5">
    {notice ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">{notice === "created" ? "Đã tạo nhân sự." : "Đã cập nhật nhân sự."}</p> : null}
    <RecordIdentity name={staff.fullName} subtitle={jobs[staff.jobTitle]}><StatusBadge tone={staff.active ? "success" : "neutral"}>{staff.active ? "Đang làm việc" : "Đã ngừng"}</StatusBadge></RecordIdentity>
    <div className="flex flex-wrap gap-3">{role === "ADMIN" ? <Link href={`/organization/staff/${staffId}/edit`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Chỉnh sửa</Link> : null}<Link href="/organization" className="inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary">Quay lại</Link></div>
    <div className="grid items-start gap-5 xl:grid-cols-2">
      <DetailSection title={role === "ADMIN" ? "Định danh & chuyên môn" : "Thông tin công tác"}>{role === "ADMIN" ? <Info label="CCCD" value={staff.identityNumber} mono /> : null}<Info label="Chức danh" value={jobs[staff.jobTitle]} /><Info label="Chuyên môn" value={staff.specialization} />{role === "ADMIN" ? <Info label="Giấy phép" value={staff.licenseNumber} /> : null}<div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Khoa công tác</dt><dd className="mt-1"><Link href={`/organization/departments/${staff.departmentId}`} className="rounded text-sm text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">Xem thông tin khoa →</Link></dd></div></DetailSection>
      {role === "ADMIN" ? <DetailSection title="Thông tin liên hệ"><Info label="Điện thoại" value={staff.phoneNumber} /><Info label="Email" value={staff.email} /></DetailSection> : null}
    </div>
    {role === "ADMIN" ? <DetailSection title="Thông tin hồ sơ"><Info label="Mã nhân sự" value={staff.staffId} mono /><Info label="Mã khoa" value={staff.departmentId} mono /><Info label="Ngày tạo" value={formatInstant(staff.createdAt)} /><Info label="Cập nhật" value={formatInstant(staff.updatedAt)} /></DetailSection> : null}
  </section>;
}
function Info({ label, value, mono = false }: { label: string; value: string | null | undefined; mono?: boolean }) { return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className={`mt-1 break-words text-sm ${mono ? "font-mono" : ""}`}>{value ?? "—"}</dd></div>; }
