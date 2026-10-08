"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { isUuid } from "@/lib/validation";
import { organizationApi } from "../api";
import type { DepartmentType } from "../types";

interface Props { departmentId?: string }
const typeOptions: { value: DepartmentType; label: string }[] = [{ value: "CLINICAL", label: "Lâm sàng" }, { value: "PARACLINICAL", label: "Cận lâm sàng" }, { value: "ADMINISTRATIVE", label: "Hành chính" }];

export function DepartmentForm({ departmentId }: Props) {
  const router = useRouter(); const editing = Boolean(departmentId);
  const [name, setName] = useState(""); const [abbreviation, setAbbreviation] = useState(""); const [type, setType] = useState<DepartmentType>("CLINICAL"); const [location, setLocation] = useState(""); const [headId, setHeadId] = useState(""); const [active, setActive] = useState(true);
  const [loading, setLoading] = useState(editing); const [saving, setSaving] = useState(false); const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!departmentId) return;
    let mounted = true;
    const timer = window.setTimeout(() => organizationApi.department(departmentId).then((department) => {
      if (!mounted) return; setName(department.departmentName); setAbbreviation(department.abbreviation); setType(department.departmentType); setLocation(department.location ?? ""); setHeadId(department.departmentHeadId ?? ""); setActive(department.active);
    }).catch((cause: unknown) => { if (mounted) setError(cause instanceof Error ? cause.message : "Không thể tải khoa."); }).finally(() => { if (mounted) setLoading(false); }), 0);
    return () => { mounted = false; window.clearTimeout(timer); };
  }, [departmentId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const normalizedHead = headId.trim();
    if (normalizedHead && !isUuid(normalizedHead)) { setError("Mã trưởng khoa phải là UUID hợp lệ."); return; }
    setSaving(true); setError(null);
    try {
      const department = departmentId
        ? await organizationApi.updateDepartment(departmentId, { departmentName: name.trim(), departmentType: type, location: location.trim() || null, departmentHeadId: normalizedHead || null, active })
        : await organizationApi.createDepartment({ departmentName: name.trim(), abbreviation: abbreviation.trim(), departmentType: type, location: location.trim() || null });
      router.push(`/organization/departments/${department.departmentId}?notice=${editing ? "updated" : "created"}`);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể lưu khoa.");
    } finally { setSaving(false); }
  }

  if (loading) return <AsyncState kind="loading" message="Đang tải thông tin khoa…" />;
  return <form onSubmit={submit} className="mt-6 space-y-6">{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}<div className="grid gap-5 rounded-xl border border-border bg-surface p-6 md:grid-cols-2"><Field label="Tên khoa" htmlFor="department-name" required><input id="department-name" required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} className={controlClassName} /></Field><Field label="Viết tắt" htmlFor="department-abbreviation" required hint={editing ? "Mã viết tắt không đổi sau khi tạo." : undefined}><input id="department-abbreviation" required={!editing} disabled={editing} maxLength={20} value={abbreviation} onChange={(event) => setAbbreviation(event.target.value)} className={controlClassName} /></Field><Field label="Loại khoa" htmlFor="department-type" required><select id="department-type" value={type} onChange={(event) => setType(event.target.value as DepartmentType)} className={controlClassName}>{typeOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></Field><Field label="Địa điểm" htmlFor="department-location"><input id="department-location" maxLength={255} value={location} onChange={(event) => setLocation(event.target.value)} className={controlClassName} /></Field>{editing ? <><Field label="Mã trưởng khoa" htmlFor="department-head"><input id="department-head" value={headId} onChange={(event) => setHeadId(event.target.value)} className={controlClassName} /></Field><Field label="Trạng thái" htmlFor="department-active"><select id="department-active" value={String(active)} onChange={(event) => setActive(event.target.value === "true")} className={controlClassName}><option value="true">Đang hoạt động</option><option value="false">Ngừng hoạt động</option></select></Field></> : null}</div><div className="flex gap-2"><Button type="submit" variant="primary" loading={saving}>{editing ? "Lưu thay đổi" : "Tạo khoa"}</Button><Button type="button" disabled={saving} onClick={() => router.back()}>Quay lại</Button></div></form>;
}
