"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useUnsavedChanges } from "../use-unsaved-changes";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/auth";
import { isUuid } from "@/lib/validation";
import { FormSection } from "./RecordLayout";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import { departmentCategories, departmentCategoryHints, departmentCategoryLabels, roomTypesFor } from "../rooms";
import { departmentMatchesDraft, isSaveOutcomeUnknown, type DepartmentSaveDraft } from "../save-outcome";
import { SaveOutcomeRecovery } from "./SaveOutcomeRecovery";
import type { DepartmentType, RoomTypeDTO } from "../types";

interface Props { departmentId?: string }

export function DepartmentForm({ departmentId }: Props) {
  return <DepartmentFormWorkspace key={departmentId ?? "new"} departmentId={departmentId} />;
}

function DepartmentFormWorkspace({ departmentId }: Props) {
  const { localize } = useLocale();
  const { markDirty, markSaved, confirmLeave } = useUnsavedChanges();
  const router = useRouter(); const editing = Boolean(departmentId);
  const [name, setName] = useState(""); const [abbreviation, setAbbreviation] = useState(""); const [type, setType] = useState<DepartmentType>("CLINICAL"); const [location, setLocation] = useState(""); const [headId, setHeadId] = useState(""); const [active, setActive] = useState(true);
  const [description, setDescription] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const unit = type === "ADMINISTRATIVE" ? "phòng" : "khoa";
  const [loading, setLoading] = useState(editing);
  const [loadedDepartmentId, setLoadedDepartmentId] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<{ key: string; message: string } | null>(null);
  const [roomTypes, setRoomTypes] = useState<RoomTypeDTO[] | null>(null);
  const mounted = useRef(true);
  const submitting = useRef(false);
  const unresolved = useRef<DepartmentSaveDraft | null>(null);
  const [unknownSave, setUnknownSave] = useState<DepartmentSaveDraft | null>(null);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  // Room types of each category, so the admin sees which workplaces the unit can have.
  useEffect(() => {
    let alive = true;
    Promise.resolve().then(() => organizationApi.roomTypes())
      .then(rows => { if (alive) setRoomTypes(rows); })
      .catch(() => { if (alive) setRoomTypes(null); });
    return () => { alive = false; };
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
      setDescription(department.description ?? ""); setPhone(department.phoneNumber ?? ""); setEmail(department.email ?? "");
      setHeadId(department.departmentHeadId ?? "");
      setActive(department.active);
      setLoadedDepartmentId(departmentId);
    }).catch((cause: unknown) => {
      if (!mounted || !isRequestSessionCurrent(sessionToken, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      setLoadError({ key: departmentId, message: cause instanceof Error ? cause.message : "Không thể tải khoa/phòng." });
    }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [departmentId, retryToken, router]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const normalizedHead = headId.trim();
    if (normalizedHead && !isUuid(normalizedHead)) { setError(`Mã trưởng ${unit} phải là UUID hợp lệ.`); return; }
    const sessionToken = getSessionKey();
    submitting.current = true;
    setSaving(true); setError(null);
    const profile = { description: description.trim() || null, phoneNumber: phone.trim() || null, email: email.trim() || null };
    const draft: DepartmentSaveDraft = { departmentName: name.trim(), abbreviation: abbreviation.trim(),
      departmentType: type, location: location.trim() || null, departmentHeadId: normalizedHead || null, active, profile };
    let completed = false;
    try {
      const department = departmentId
        ? await organizationApi.updateDepartment(departmentId, { departmentName: name.trim(), departmentType: type, location: location.trim() || null, departmentHeadId: normalizedHead || null, active, profile })
        : await organizationApi.createDepartment({ departmentName: name.trim(), abbreviation: abbreviation.trim(), departmentType: type, location: location.trim() || null, profile });
      if (!mounted.current || getSessionKey() !== sessionToken) return;
      completed = true;
      setSaved(true);
      markSaved(); router.push(`/organization/departments/${department.departmentId}?notice=${editing ? "updated" : "created"}`);
    } catch (cause: unknown) {
      if (!mounted.current || !isRequestSessionCurrent(sessionToken, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else {
        if (isSaveOutcomeUnknown(cause)) { unresolved.current = draft; setUnknownSave(draft); markDirty(); }
        setError(cause instanceof ApiRequestError && cause.code === "DEPARTMENT_TYPE_HAS_ROOMS"
          ? "Khoa/bộ phận đã có phòng làm việc nên không thể đổi khối. Loại phòng gắn với khối; hãy giữ khối hiện tại hoặc tạo đơn vị mới."
          : cause instanceof Error ? cause.message : "Không thể lưu khoa/phòng.");
      }
    } finally {
      submitting.current = completed || unresolved.current !== null;
      if (mounted.current && getSessionKey() === sessionToken) setSaving(false);
    }
  }

  const currentLoadError = loadError;
  if (editing && currentLoadError && currentLoadError.key === departmentId) {
    const message = currentLoadError.message;
    return <AsyncState kind="error" message={localize(message)} onRetry={() => { setLoading(true); setLoadError(null); setRetryToken((value) => value + 1); }} />;
  }
  if (loading || (editing && loadedDepartmentId !== departmentId)) return <AsyncState kind="loading" message={localize("Đang tải thông tin khoa/phòng…")} />;
  return <form onChangeCapture={markDirty} onSubmit={submit} className="mt-6 max-w-5xl space-y-5 [&_input]:scroll-mb-32 [&_select]:scroll-mb-32 [&_textarea]:scroll-mb-32">
    <L10nValue>{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><L10nValue>{error}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{unknownSave ? <SaveOutcomeRecovery read={async () => {
      if (departmentId) return organizationApi.department(departmentId);
      const session = getSessionKey();
      const all = await organizationApi.departments(false);
      if (!mounted.current || getSessionKey() !== session) return null;
      const found = all.find(item => item.abbreviation === unknownSave.abbreviation);
      return found ? organizationApi.department(found.departmentId) : null;
    }} matches={department => departmentMatchesDraft(department, unknownSave)}
      onUnauthorized={() => router.replace("/login")} onVerified={department => {
        markSaved(); router.push(`/organization/departments/${department.departmentId}?notice=${editing ? "updated" : "created"}`);
      }} /> : null}</L10nValue>
    <p className="text-sm text-muted-foreground"><L10n>{"Các trường có dấu * là bắt buộc."}</L10n></p>
    <fieldset disabled={saving || Boolean(unknownSave)} className="min-w-0 space-y-5">
      <FormSection title={localize(`Thông tin ${unit}`)} description={localize("Tên, mã viết tắt và phân loại đơn vị trong tổ chức.")}>
        <Field label={localize(`Tên ${unit}`)} htmlFor="department-name" required><input id="department-name" required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} className={controlClassName} /></Field>
        <Field label={localize("Viết tắt")} htmlFor="department-abbreviation" required hint={editing ? "Mã viết tắt không đổi sau khi tạo." : undefined}><input id="department-abbreviation" required={!editing} disabled={editing} maxLength={20} value={abbreviation} onChange={(event) => setAbbreviation(event.target.value)} className={controlClassName} /></Field>
        <Field label={localize(`Loại ${unit}`)} htmlFor="department-type" required><select id="department-type" value={type} onChange={(event) => setType(event.target.value as DepartmentType)} className={controlClassName}><L10nValue>{departmentCategories.map((option) => <option key={option} value={option}><L10nValue>{departmentCategoryLabels[option]}</L10nValue></option>)}</L10nValue></select>
          <span className="mt-1 block text-xs font-normal text-muted-foreground">{departmentCategoryHints[type]}<L10nValue>{roomTypes ? ` Loại phòng của khối: ${roomTypesFor(roomTypes, type).map(row => row.name).join(", ") || "chưa có (thêm tại Danh mục khoa phòng)"}.` : ""}</L10nValue></span></Field>
        <Field label={localize("Địa điểm")} htmlFor="department-location"><input id="department-location" maxLength={255} value={location} onChange={(event) => setLocation(event.target.value)} className={controlClassName} /></Field>
      </FormSection>
      <FormSection title={localize("Chức năng và liên hệ")} description={localize("Nhiệm vụ và đầu mối liên hệ của đơn vị.")}>
        <Field label={localize("Chức năng / nhiệm vụ")} htmlFor="department-description"><textarea id="department-description" rows={4} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} className={controlClassName} /></Field>
        <Field label={localize("Điện thoại / máy lẻ")} htmlFor="department-phone"><input id="department-phone" type="tel" maxLength={30} pattern={"[0-9+\\(\\) .\\-]{3,30}"} value={phone} onChange={event => setPhone(event.target.value)} className={controlClassName} /></Field>
        <Field label={localize("Email đơn vị")} htmlFor="department-email"><input id="department-email" type="email" maxLength={100} value={email} onChange={event => setEmail(event.target.value)} className={controlClassName} /></Field>
      </FormSection>
      <L10nValue>{editing ? <FormSection title={localize(`Quản lý ${unit}`)} description={localize("Phân công người phụ trách và quản lý trạng thái hoạt động.")}>
        <p className="text-sm text-muted-foreground"><L10n>{"Bổ nhiệm hoặc miễn nhiệm trưởng khoa lâm sàng tại trang chi tiết khoa, kèm lý do và lịch sử. Biểu mẫu này giữ nguyên người phụ trách hiện tại."}</L10n></p>
        <Field label={localize("Trạng thái")} htmlFor="department-active"><select id="department-active" value={String(active)} onChange={(event) => setActive(event.target.value === "true")} className={controlClassName}><option value="true"><L10n>{"Đang hoạt động"}</L10n></option><option value="false"><L10n>{"Ngừng hoạt động"}</L10n></option></select></Field>
      </FormSection> : null}</L10nValue>
    </fieldset>
    <div className="sticky bottom-4 z-10 flex flex-wrap justify-end gap-3 rounded-xl border border-border bg-surface p-4 shadow-lg"><Button type="button" disabled={saving} onClick={() => { if (confirmLeave()) router.back(); }}><L10n>{"Quay lại"}</L10n></Button><Button type="submit" variant="primary" loading={saving} disabled={Boolean(unknownSave) || saved}><L10nValue>{editing ? "Lưu thay đổi" : `Tạo ${unit}`}</L10nValue></Button></div>
  </form>;
}
