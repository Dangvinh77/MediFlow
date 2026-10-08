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
import type { DepartmentDTO, DepartmentType } from "../types";

const getServerRole = () => null;
const labels: Record<DepartmentType, string> = { CLINICAL: "Lâm sàng", PARACLINICAL: "Cận lâm sàng", ADMINISTRATIVE: "Hành chính" };

export function DepartmentDetail({ departmentId, notice }: { departmentId: string; notice?: "created" | "updated" }) {
  const router = useRouter(); const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole); const [department, setDepartment] = useState<DepartmentDTO | null>(null); const [error, setError] = useState<string | null>(null);
  useEffect(() => { let mounted = true; organizationApi.department(departmentId).then((value) => { if (mounted) setDepartment(value); }).catch((cause: unknown) => { if (!mounted) return; if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể tải khoa."); }); return () => { mounted = false; }; }, [departmentId, router]);
  if (error) return <AsyncState kind="error" message={error} onRetry={() => window.location.reload()} />; if (!department) return <AsyncState kind="loading" message="Đang tải khoa…" />;
  return <section className="mt-6 space-y-6">{notice ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">{notice === "created" ? "Đã tạo khoa." : "Đã cập nhật khoa."}</p> : null}<div className="flex items-start justify-between gap-4 rounded-xl border border-border bg-surface p-6"><div><p className="text-sm text-muted-foreground">{department.abbreviation}</p><h2 className="mt-1 text-xl font-semibold">{department.departmentName}</h2><p className="mt-2 break-all font-mono text-xs text-muted-foreground">{department.departmentId}</p></div><StatusBadge tone={department.active ? "success" : "neutral"}>{department.active ? "Đang hoạt động" : "Ngừng hoạt động"}</StatusBadge></div><dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2"><Info label="Loại khoa" value={labels[department.departmentType]} /><Info label="Địa điểm" value={department.location} /><Info label="Trưởng khoa" value={department.departmentHeadId} mono /><Info label="Ngày tạo" value={formatInstant(department.createdAt)} /><Info label="Cập nhật" value={formatInstant(department.updatedAt)} /></dl><div className="flex gap-2">{role === "ADMIN" ? <Link href={`/organization/departments/${departmentId}/edit`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground">Chỉnh sửa</Link> : null}<Link href="/organization" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold">Quay lại</Link></div></section>;
}

function Info({ label, value, mono = false }: { label: string; value: string | null; mono?: boolean }) { return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className={`mt-1 break-all text-sm ${mono ? "font-mono" : ""}`}>{value ?? "—"}</dd></div>; }
