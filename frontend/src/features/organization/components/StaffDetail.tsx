"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, useSyncExternalStore } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { formatInstant } from "@/lib/format";
import { organizationApi } from "../api";
import type { JobTitle, StaffDTO } from "../types";

const getServerRole = () => null;
const jobs: Record<JobTitle, string> = { DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", TECHNICIAN: "Kỹ thuật viên", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", MANAGER: "Quản lý", ADMINISTRATIVE: "Hành chính" };

export function StaffDetail({ staffId, notice }: { staffId: string; notice?: "created" | "updated" }) {
  const router = useRouter(); const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole); const [staff, setStaff] = useState<StaffDTO | null>(null); const [error, setError] = useState<string | null>(null);
  useEffect(() => { let mounted = true; organizationApi.staffMember(staffId).then((value) => { if (mounted) setStaff(value); }).catch((cause: unknown) => { if (!mounted) return; if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể tải nhân sự."); }); return () => { mounted = false; }; }, [router, staffId]);
  if (error) return <AsyncState kind="error" message={error} onRetry={() => window.location.reload()} />; if (!staff) return <AsyncState kind="loading" message="Đang tải nhân sự…" />;
  return <section className="mt-6 space-y-6">{notice ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">{notice === "created" ? "Đã tạo nhân sự." : "Đã cập nhật nhân sự."}</p> : null}<div className="flex items-start justify-between gap-4 rounded-xl border border-border bg-surface p-6"><div><h2 className="text-xl font-semibold">{staff.fullName}</h2><p className="mt-1">{jobs[staff.jobTitle]}</p><p className="mt-2 break-all font-mono text-xs text-muted-foreground">{staff.staffId}</p></div><StatusBadge tone={staff.active ? "success" : "neutral"}>{staff.active ? "Đang làm việc" : "Đã ngừng"}</StatusBadge></div><dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2 lg:grid-cols-3"><Info label="Khoa" value={staff.departmentId} mono /><Info label="Chuyên môn" value={staff.specialization} /><Info label="Giấy phép" value={staff.licenseNumber} /><Info label="Điện thoại" value={staff.phoneNumber} /><Info label="Email" value={staff.email} /><Info label="Ngày tạo" value={formatInstant(staff.createdAt)} /><Info label="Cập nhật" value={formatInstant(staff.updatedAt)} /></dl><div className="flex gap-2">{role === "ADMIN" ? <Link href={`/organization/staff/${staffId}/edit`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground">Chỉnh sửa</Link> : null}<Link href="/organization" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold">Quay lại</Link></div></section>;
}
function Info({ label, value, mono = false }: { label: string; value: string | null; mono?: boolean }) { return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className={`mt-1 break-all text-sm ${mono ? "font-mono" : ""}`}>{value ?? "—"}</dd></div>; }
