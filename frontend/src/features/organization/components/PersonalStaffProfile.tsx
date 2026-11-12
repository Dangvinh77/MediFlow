"use client";
import { L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/auth";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import type { StaffDTO, JobTitle } from "../types";
import { DetailSection, RecordIdentity } from "./RecordLayout";
import { OwnStaffDepartment } from "./OwnStaffDepartment";
import { OwnStaffContactForm } from "./OwnStaffContactForm";

const jobs: Record<JobTitle, string> = { DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", TECHNICIAN: "Kỹ thuật viên", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", ADMINISTRATIVE: "Hành chính" };

/** Mounted by the profile route with the current session and signed staff claim as its key. */
export function PersonalStaffProfile({ staffId }: { staffId: string }) {
  const { localize } = useLocale();
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
  if (!staff) return <AsyncState kind="loading" message={localize("Đang tải hồ sơ cá nhân…")} />;
  return <div className="space-y-5">
    <RecordIdentity name={staff.fullName} subtitle={jobs[staff.jobTitle]} />
    <div className="grid items-start gap-5 xl:grid-cols-2">
      <DetailSection title={localize("Thông tin cá nhân")}><Info label={localize("Họ và tên")} value={staff.fullName} /><Info label={localize("CCCD")} value={staff.identityNumber} /><Info label={localize("Ngày sinh")} value={staff.dateOfBirth?.split("-").reverse().join("/") ?? null} /><Info label={localize("Giới tính")} value={staff.gender ? { MALE: "Nam", FEMALE: "Nữ", OTHER: "Khác" }[staff.gender] : null} /><Info label={localize("Địa chỉ")} value={staff.address} /><Info label={localize("Điện thoại")} value={staff.phoneNumber} /><Info label={localize("Email")} value={staff.email} /></DetailSection>
      <DetailSection title={localize("Thông tin công tác")}><OwnStaffDepartment staffId={staff.staffId} /><Info label={localize("Chức danh")} value={jobs[staff.jobTitle]} /><Info label={localize("Chuyên môn")} value={staff.specialization} /><Info label={localize("Trình độ chuyên môn")} value={staff.qualification} /><Info label={localize("Giấy phép")} value={staff.licenseNumber} /><Info label={localize("Ngày vào làm")} value={staff.hireDate?.split("-").reverse().join("/") ?? null} /><Info label={localize("Trạng thái")} value={staff.active ? "Đang làm việc" : "Đã ngừng làm việc"} /></DetailSection>
    </div>
    <OwnStaffContactForm key={staff.staffId} staff={staff} onSaved={setStaff} />
  </div>;
}

function Info({ label, value }: { label: string; value: string | null }) {
  return <div><dt className="text-sm text-muted-foreground"><L10nValue>{label}</L10nValue></dt><dd className="mt-1.5 break-words text-sm leading-6"><L10nValue>{value ?? "—"}</L10nValue></dd></div>;
}
