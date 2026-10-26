"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import { PageShell } from "@/components/layout/PageShell";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { DetailSection, RecordIdentity, RecordBreadcrumb } from "./RecordLayout";
import { DepartmentPeople } from "./DepartmentPeople";
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
  useEffect(() => { let mounted = true; const sessionToken = getSessionKey(); organizationApi.department(departmentId).then((value) => { if (mounted && isRequestSessionCurrent(sessionToken)) setDepartment(value); }).catch((cause: unknown) => { if (!mounted || !isRequestSessionCurrent(sessionToken, cause)) return; if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể tải thông tin khoa/phòng."); }); return () => { mounted = false; }; }, [departmentId, router, attempt]);
  const unit = department ? department.departmentType === "ADMINISTRATIVE" ? "phòng" : "khoa" : "khoa/phòng";
  const title = `Chi tiết ${unit}`;
  function layout(content: ReactNode) {
    return <PageShell title={title} description="Thông tin tổ chức và trạng thái vận hành."><RecordBreadcrumb href="/organization" parent="Tổ chức" current={title} />{content}</PageShell>;
  }
  if (error) return layout(<AsyncState kind="error" message={error} onRetry={() => { setError(null); setAttempt((value) => value + 1); }} />);
  if (!department) return layout(<AsyncState kind="loading" message="Đang tải thông tin khoa/phòng…" />);
  return layout(<section className="mt-6 space-y-5">
    {notice ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">{notice === "created" ? `Đã tạo ${unit}.` : `Đã cập nhật ${unit}.`}</p> : null}
    <RecordIdentity name={department.departmentName} subtitle={department.abbreviation}><StatusBadge tone={department.active ? "success" : "neutral"}>{department.active ? "Đang hoạt động" : "Ngừng hoạt động"}</StatusBadge>{role === "ADMIN" ? <Link href={`/organization/departments/${departmentId}/edit`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Chỉnh sửa</Link> : null}</RecordIdentity>
    <div className="flex flex-wrap gap-3"><Link href="/organization" className="inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary">Quay lại</Link></div>
    <DetailSection title={`Thông tin ${unit}`}><Info label={`Loại ${unit}`} value={labels[department.departmentType]} /><Info label="Địa điểm" value={department.location} /><Info label="Chức năng, nhiệm vụ" value={department.description} /><Info label="Điện thoại / máy lẻ" value={department.phoneNumber} /><Info label="Email liên hệ" value={department.email} /></DetailSection>
    <DepartmentPeople key={departmentId} department={department} />
  </section>);
}

function Info({ label, value, mono = false }: { label: string; value: string | null | undefined; mono?: boolean }) { return <div><dt className="text-sm text-muted-foreground">{label}</dt><dd className={`mt-1.5 break-words text-sm leading-6 ${mono ? "font-mono" : ""}`}>{value ?? "—"}</dd></div>; }
