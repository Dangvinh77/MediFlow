"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useUnsavedChanges } from "../use-unsaved-changes";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useSyncExternalStore, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import { FormSection } from "./RecordLayout";
import { patientToday } from "../logic";
import { usePatientForm } from "../usePatientForm";
import { PatientSaveRecovery } from "./PatientSaveRecovery";

interface PatientFormProps { patientId?: string }
const getServerSession = () => null;

export function PatientForm({ patientId }: PatientFormProps) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, getServerSession);
  return <PatientFormWorkspace key={`${patientId ?? "create"}:${session}`} patientId={patientId} />;
}

function PatientFormWorkspace({ patientId }: PatientFormProps) {
  const { localize } = useLocale();
  const router = useRouter();
  const { markDirty, markSaved, confirmLeave } = useUnsavedChanges();
  const editing = Boolean(patientId);
  const { form, set, identityChanged, identityReason, setIdentityReason, emailRequired, loading, loaded, saving, blocked, outcomeUnknown, error, fieldErrors, save, retry } = usePatientForm(patientId, markSaved);
  const errorSummary = useRef<HTMLDivElement>(null);
  useEffect(() => { if (error) errorSummary.current?.focus(); }, [error]);
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); void save(); }

  if (loading) return <AsyncState kind="loading" message={localize("Đang tải thông tin bệnh nhân…")} />;
  if (error && editing && !loaded) return <AsyncState kind="error" message={localize(error.message)} correlationId={error.correlationId} onRetry={retry} />;

  return (
    <form noValidate onChangeCapture={markDirty} onSubmit={submit} className="mt-6 max-w-5xl space-y-5 [&_input]:scroll-mb-32 [&_select]:scroll-mb-32 [&_textarea]:scroll-mb-32">
      <L10nValue>{error ? <div role="alert" aria-label={localize("Không thể lưu bệnh nhân")} ref={errorSummary} tabIndex={-1} className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><p><L10nValue>{error.message}</L10nValue></p><L10nValue>{error.correlationId ? <p className="mt-1 font-mono text-xs"><L10n>{"Mã theo dõi: "}</L10n>{error.correlationId}</p> : null}</L10nValue></div> : null}</L10nValue>
      <p className="text-sm text-muted-foreground"><L10n>{"Các trường có dấu * là bắt buộc."}</L10n></p>
      <L10nValue>{outcomeUnknown ? <PatientSaveRecovery patientId={patientId} draft={form} onVerified={patient => { markSaved(); router.push(`/patients/${encodeURIComponent(patient.maBenhNhan)}`); }} /> : null}</L10nValue>
      <fieldset disabled={blocked} className="min-w-0 space-y-5">
        <L10nValue>{identityChanged ? <FormSection title={localize("Xác nhận thay đổi định danh")} description={localize("CCCD hoặc ngày sinh đã thay đổi. Lý do và người thực hiện sẽ được lưu vào lịch sử.")}><Field label={localize("Lý do thay đổi định danh")} htmlFor="patient-identity-reason" required><textarea id="patient-identity-reason" required maxLength={500} rows={3} value={identityReason} onChange={event => setIdentityReason(event.target.value)} className={controlClassName} /></Field></FormSection> : null}</L10nValue>
        <FormSection title={localize("Thông tin cá nhân")} description={localize("Thông tin định danh của bệnh nhân. Kiểm tra kỹ CMND/CCCD trước khi lưu.")}>
          <Field label={localize("Họ tên")} error={fieldErrors.hoTen} htmlFor="patient-name" required><input id="patient-name" aria-invalid={Boolean(fieldErrors.hoTen)} aria-describedby={fieldErrors.hoTen ? "patient-name-error" : undefined} autoComplete="name" required maxLength={100} value={form.hoTen} onChange={(event) => set("hoTen", event.target.value)} className={controlClassName} /></Field>
          <Field label={localize("Ngày sinh")} error={fieldErrors.ngaySinh} htmlFor="patient-birth-date" required><input id="patient-birth-date" aria-invalid={Boolean(fieldErrors.ngaySinh)} aria-describedby={fieldErrors.ngaySinh ? "patient-birth-date-error" : undefined} required type="date" max={patientToday()} value={form.ngaySinh} onChange={(event) => set("ngaySinh", event.target.value)} className={controlClassName} /></Field>
          <Field label={localize("Giới tính")} error={fieldErrors.gioiTinh} htmlFor="patient-gender" required><select id="patient-gender" aria-invalid={Boolean(fieldErrors.gioiTinh)} aria-describedby={fieldErrors.gioiTinh ? "patient-gender-error" : undefined} value={form.gioiTinh} onChange={(event) => set("gioiTinh", event.target.value)} className={controlClassName}><option value="M"><L10n>{"Nam"}</L10n></option><option value="F"><L10n>{"Nữ"}</L10n></option></select></Field>
          <Field label={localize("Số CMND/CCCD")} error={fieldErrors.soCmnd} htmlFor="patient-identity" required><input id="patient-identity" aria-invalid={Boolean(fieldErrors.soCmnd)} aria-describedby={fieldErrors.soCmnd ? "patient-identity-error" : undefined} required maxLength={20} value={form.soCmnd} onChange={(event) => set("soCmnd", event.target.value)} className={controlClassName} /></Field>
        </FormSection>
        <FormSection title={localize("Liên hệ & bảo hiểm")} description={localize("Thông tin liên lạc và số bảo hiểm y tế, nếu có.")}>
          <Field label={localize("Số điện thoại")} error={fieldErrors.soDienThoai} htmlFor="patient-phone" hint="10 đến 15 chữ số."><input id="patient-phone" aria-invalid={Boolean(fieldErrors.soDienThoai)} aria-describedby={fieldErrors.soDienThoai ? "patient-phone-error" : "patient-phone-hint"} type="tel" autoComplete="tel" inputMode="numeric" pattern="[0-9]{10,15}" value={form.soDienThoai} onChange={(event) => set("soDienThoai", event.target.value)} className={controlClassName} /></Field>
          <Field label={localize("Email")} error={fieldErrors.email} htmlFor="patient-email" required={emailRequired} hint={editing ? "Điều dưỡng hoặc quản trị viên có thể bổ sung email. Nếu đã có tài khoản, đổi email đăng nhập tại chi tiết bệnh nhân để đồng bộ hồ sơ." : "Email nhận hướng dẫn đăng nhập và khôi phục mật khẩu; quản lý tài khoản tại chi tiết bệnh nhân sau khi lưu."}><input id="patient-email" aria-invalid={Boolean(fieldErrors.email)} aria-describedby={fieldErrors.email ? "patient-email-error" : undefined} type="email" autoComplete="email" required={emailRequired} maxLength={254} value={form.email} onChange={(event) => set("email", event.target.value)} className={controlClassName} /></Field>
          <Field label={localize("Số BHYT")} error={fieldErrors.bhytSo} htmlFor="patient-insurance" hint="Không bắt buộc. Định dạng hiện tại: 01-12345678-9 (2 số-8 số-1 số, có dấu gạch nối)."><input id="patient-insurance" aria-invalid={Boolean(fieldErrors.bhytSo)} aria-describedby={fieldErrors.bhytSo ? "patient-insurance-error" : "patient-insurance-hint"} placeholder="01-12345678-9" pattern="[0-9]{2}-[0-9]{8}-[0-9]" value={form.bhytSo} onChange={(event) => set("bhytSo", event.target.value)} className={controlClassName} /></Field>
          <Field label={localize("Địa chỉ")} error={fieldErrors.diaChi} htmlFor="patient-address"><textarea id="patient-address" aria-invalid={Boolean(fieldErrors.diaChi)} aria-describedby={fieldErrors.diaChi ? "patient-address-error" : undefined} autoComplete="street-address" maxLength={255} rows={3} value={form.diaChi} onChange={(event) => set("diaChi", event.target.value)} className={controlClassName} /></Field>
        </FormSection>
      </fieldset>
      <div className="sticky bottom-4 z-10 flex flex-wrap justify-end gap-3 rounded-xl border border-border bg-surface p-4 shadow-lg"><Button type="button" disabled={saving} onClick={() => { if (confirmLeave()) router.back(); }}><L10n>{"Quay lại"}</L10n></Button><Button type="submit" variant="primary" loading={saving} disabled={blocked}><L10nValue>{editing ? "Lưu thay đổi" : "Tạo bệnh nhân"}</L10nValue></Button></div>
    </form>
  );
}
