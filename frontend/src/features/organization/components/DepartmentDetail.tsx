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
import type { DepartmentDTO, DepartmentType } from "../types";

const getServerRole = () => null;
const labels: Record<DepartmentType, string> = { CLINICAL: "Lâm sàng", PARACLINICAL: "Cận lâm sàng", ADMINISTRATIVE: "Hành chính" };

export function DepartmentDetail({ departmentId, notice }: { departmentId: string; notice?: "created" | "updated" }) {
  return <DepartmentDetailWorkspace key={departmentId} departmentId={departmentId} notice={notice} />;
}

function DepartmentDetailWorkspace({ departmentId, notice }: { departmentId: string; notice?: "created" | "updated" }) {
  const router = useRouter(); const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole); const [department, setDepartment] = useState<DepartmentDTO | null>(null); const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => { let mounted = true; const sessionToken = getSessionKey(); organizationApi.department(departmentId).then((value) => { if (mounted && isRequestSessionCurrent(sessionToken)) setDepartment(value); }).catch((cause: unknown) => { if (!mounted || !isRequestSessionCurrent(sessionToken, cause)) return; if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể tải khoa."); }); return () => { mounted = false; }; }, [departmentId, router, attempt]);
  if (error) return <AsyncState kind="error" message={error} onRetry={() => { setError(null); setAttempt((value) => value + 1); }} />; if (!department) return <AsyncState kind="loading" message="Đang tải khoa…" />;
  return <section className="mt-6 space-y-5">
    {notice ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">{notice === "created" ? "Đã tạo khoa." : "Đã cập nhật khoa."}</p> : null}
    <RecordIdentity name={department.departmentName} subtitle={department.abbreviation}><StatusBadge tone={department.active ? "success" : "neutral"}>{department.active ? "Đang hoạt động" : "Ngừng hoạt động"}</StatusBadge></RecordIdentity>
    <div className="flex flex-wrap gap-3">{role === "ADMIN" ? <Link href={`/organization/departments/${departmentId}/edit`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Chỉnh sửa</Link> : null}<Link href="/organization" className="inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary">Quay lại</Link></div>
    <DetailSection title="Thông tin khoa phòng"><Info label="Loại khoa" value={labels[department.departmentType]} /><Info label="Địa điểm" value={department.location} /><div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Trưởng khoa</dt><dd className="mt-1 text-sm">{department.departmentHeadId ? <Link href={`/organization/staff/${department.departmentHeadId}`} className="rounded text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">Xem hồ sơ trưởng khoa →</Link> : "Chưa phân công"}</dd></div></DetailSection>
    <DetailSection title="Thông tin hồ sơ"><Info label="Mã khoa" value={department.departmentId} mono /><Info label="Mã trưởng khoa" value={department.departmentHeadId} mono /><Info label="Ngày tạo" value={formatInstant(department.createdAt)} /><Info label="Cập nhật" value={formatInstant(department.updatedAt)} /></DetailSection>
  </section>;
}

function Info({ label, value, mono = false }: { label: string; value: string | null | undefined; mono?: boolean }) { return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className={`mt-1 break-words text-sm ${mono ? "font-mono" : ""}`}>{value ?? "—"}</dd></div>; }
