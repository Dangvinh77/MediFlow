"use client";

import { useUnsavedChanges } from "../use-unsaved-changes";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/auth";
import { isUuid } from "@/lib/validation";
import { StaffPicker } from "./StaffPicker";
import { FormSection } from "./RecordLayout";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import type { DepartmentType } from "../types";

interface Props { departmentId?: string }
const typeOptions: { value: DepartmentType; label: string }[] = [{ value: "CLINICAL", label: "Lâm sàng" }, { value: "PARACLINICAL", label: "Cận lâm sàng" }, { value: "ADMINISTRATIVE", label: "Hành chính" }];

export function DepartmentForm({ departmentId }: Props) {
  return <DepartmentFormWorkspace key={departmentId ?? "new"} departmentId={departmentId} />;
}

function DepartmentFormWorkspace({ departmentId }: Props) {
  const { markDirty, markSaved, confirmLeave } = useUnsavedChanges();
  const router = useRouter(); const editing = Boolean(departmentId);
  const [name, setName] = useState(""); const [abbreviation, setAbbreviation] = useState(""); const [type, setType] = useState<DepartmentType>("CLINICAL"); const [location, setLocation] = useState(""); const [headId, setHeadId] = useState(""); const [active, setActive] = useState(true);
  const [loading, setLoading] = useState(editing);
  const [loadedDepartmentId, setLoadedDepartmentId] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<{ key: string; message: string } | null>(null);
  const mounted = useRef(true);
  const submitting = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (!departmentId) return;
    let mounted = true;
    const sessionToken = getSessionKey();
    organizationApi.department(departmentId).then((department) => {
      if (!mounted || !isRequestSessionCurrent(sessionToken)) return;
      setName(department.departmentName);
      setAbbreviation(department.abbreviation);
      setType(department.departmentType);
      setLocation(department.location ?? "");
      setHeadId(department.departmentHeadId ?? "");
      setActive(department.active);
      setLoadedDepartmentId(departmentId);
    }).catch((cause: unknown) => {
      if (!mounted || !isRequestSessionCurrent(sessionToken, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      setLoadError({ key: departmentId, message: cause instanceof Error ? cause.message : "Không thể tải khoa." });
    }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [departmentId, retryToken, router]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const normalizedHead = headId.trim();
    if (normalizedHead && !isUuid(normalizedHead)) { setError("Mã trưởng khoa phải là UUID hợp lệ."); return; }
    const sessionToken = getSessionKey();
    submitting.current = true;
    setSaving(true); setError(null);
    try {
      const department = departmentId
        ? await organizationApi.updateDepartment(departmentId, { departmentName: name.trim(), departmentType: type, location: location.trim() || null, departmentHeadId: normalizedHead || null, active })
        : await organizationApi.createDepartment({ departmentName: name.trim(), abbreviation: abbreviation.trim(), departmentType: type, location: location.trim() || null });
      if (!mounted.current || getSessionKey() !== sessionToken) return;
      markSaved(); router.push(`/organization/departments/${department.departmentId}?notice=${editing ? "updated" : "created"}`);
    } catch (cause: unknown) {
      if (!mounted.current || !isRequestSessionCurrent(sessionToken, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể lưu khoa.");
    } finally { submitting.current = false; if (mounted.current && getSessionKey() === sessionToken) setSaving(false); }
  }

  const currentLoadError = loadError;
  if (editing && currentLoadError && currentLoadError.key === departmentId) {
    const message = currentLoadError.message;
    return <AsyncState kind="error" message={message} onRetry={() => { setLoading(true); setLoadError(null); setRetryToken((value) => value + 1); }} />;
  }
  if (loading || (editing && loadedDepartmentId !== departmentId)) return <AsyncState kind="loading" message="Đang tải thông tin khoa…" />;
  return <form onChangeCapture={markDirty} onSubmit={submit} className="mt-6 max-w-5xl space-y-5">
    {error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}
    <p className="text-sm text-muted-foreground">Các trường có dấu * là bắt buộc.</p>
    <fieldset disabled={saving} className="min-w-0 space-y-5">
      <FormSection title="Thông tin khoa phòng" description="Tên, mã viết tắt và phân loại khoa trong tổ chức.">
        <Field label="Tên khoa" htmlFor="department-name" required><input id="department-name" required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} className={controlClassName} /></Field>
        <Field label="Viết tắt" htmlFor="department-abbreviation" required hint={editing ? "Mã viết tắt không đổi sau khi tạo." : undefined}><input id="department-abbreviation" required={!editing} disabled={editing} maxLength={20} value={abbreviation} onChange={(event) => setAbbreviation(event.target.value)} className={controlClassName} /></Field>
        <Field label="Loại khoa" htmlFor="department-type" required><select id="department-type" value={type} onChange={(event) => setType(event.target.value as DepartmentType)} className={controlClassName}>{typeOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></Field>
        <Field label="Địa điểm" htmlFor="department-location"><input id="department-location" maxLength={255} value={location} onChange={(event) => setLocation(event.target.value)} className={controlClassName} /></Field>
      </FormSection>
      {editing ? <FormSection title="Quản lý khoa" description="Phân công trưởng khoa và quản lý trạng thái hoạt động.">
        <StaffPicker id="department-head" label="Trưởng khoa" value={headId} onChange={setHeadId} departmentId={departmentId} jobTitle="DOCTOR" disabled={saving} />
        <Field label="Trạng thái" htmlFor="department-active"><select id="department-active" value={String(active)} onChange={(event) => setActive(event.target.value === "true")} className={controlClassName}><option value="true">Đang hoạt động</option><option value="false">Ngừng hoạt động</option></select></Field>
      </FormSection> : null}
    </fieldset>
    <div className="flex flex-wrap justify-end gap-3 border-t border-border pt-5"><Button type="button" disabled={saving} onClick={() => { if (confirmLeave()) router.back(); }}>Quay lại</Button><Button type="submit" variant="primary" loading={saving}>{editing ? "Lưu thay đổi" : "Tạo khoa"}</Button></div>
  </form>;
}
