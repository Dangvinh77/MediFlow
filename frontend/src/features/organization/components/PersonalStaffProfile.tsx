"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/auth";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import type { StaffDTO, JobTitle } from "../types";
import { DetailSection, RecordIdentity } from "./RecordLayout";

const jobs: Record<JobTitle, string> = { DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", TECHNICIAN: "Kỹ thuật viên", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", MANAGER: "Quản lý", ADMINISTRATIVE: "Hành chính" };

/** Mounted by the profile route with the current session and signed staff claim as its key. */
export function PersonalStaffProfile({ staffId }: { staffId: string }) {
  const router = useRouter();
  const [staff, setStaff] = useState<StaffDTO | null>(null);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    const session = getSessionKey();
    organizationApi.staffMember(staffId).then((value) => {
      if (active && isRequestSessionCurrent(session)) setStaff(value);
    }).catch((cause: unknown) => {
      if (!active || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setError({ message: cause instanceof Error ? cause.message : "Không thể tải hồ sơ cá nhân.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    });
    return () => { active = false; };
  }, [staffId, attempt, router]);
  if (error) return <AsyncState kind="error" {...error} onRetry={() => { setError(null); setAttempt((value) => value + 1); }} />;
  if (!staff) return <AsyncState kind="loading" message="Đang tải hồ sơ cá nhân…" />;
  return <div className="space-y-5">
    <RecordIdentity name={staff.fullName} subtitle={jobs[staff.jobTitle]} />
    <div className="grid items-start gap-5 xl:grid-cols-2">
      <DetailSection title="Thông tin cá nhân"><Info label="Họ và tên" value={staff.fullName} /><Info label="CCCD" value={staff.identityNumber} /><Info label="Điện thoại" value={staff.phoneNumber} /><Info label="Email" value={staff.email} /></DetailSection>
      <DetailSection title="Thông tin công tác"><Info label="Chức danh" value={jobs[staff.jobTitle]} /><Info label="Chuyên môn" value={staff.specialization} /><Info label="Giấy phép" value={staff.licenseNumber} /><Info label="Trạng thái" value={staff.active ? "Đang làm việc" : "Đã ngừng làm việc"} /></DetailSection>
    </div>
    <details className="rounded-xl border border-border bg-surface p-5"><summary className="cursor-pointer text-sm font-semibold">Thông tin kỹ thuật</summary><dl className="mt-4 grid gap-4 sm:grid-cols-2"><Info label="Mã nhân sự" value={staff.staffId} /><Info label="Mã khoa công tác" value={staff.departmentId} /></dl></details>
    <p className="text-sm text-muted-foreground">Nếu cần cập nhật thông tin cá nhân hoặc công tác, vui lòng liên hệ quản trị viên.</p>
  </div>;
}

function Info({ label, value }: { label: string; value: string | null }) {
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm">{value ?? "—"}</dd></div>;
}
