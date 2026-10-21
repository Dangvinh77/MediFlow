"use client";

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

interface PatientFormProps { patientId?: string }
const getServerSession = () => null;

export function PatientForm({ patientId }: PatientFormProps) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, getServerSession);
  return <PatientFormWorkspace key={`${patientId ?? "create"}:${session}`} patientId={patientId} />;
}

function PatientFormWorkspace({ patientId }: PatientFormProps) {
  const router = useRouter();
  const { markDirty, markSaved, confirmLeave } = useUnsavedChanges();
  const editing = Boolean(patientId);
  const { form, set, loading, loaded, saving, blocked, error, fieldErrors, save, retry } = usePatientForm(patientId, markSaved);
  const errorSummary = useRef<HTMLDivElement>(null);
  useEffect(() => { if (error) errorSummary.current?.focus(); }, [error]);
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); void save(); }

  if (loading) return <AsyncState kind="loading" message="Đang tải thông tin bệnh nhân…" />;
  if (error && editing && !loaded) return <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={retry} />;

  return (
    <form onChangeCapture={markDirty} onSubmit={submit} className="mt-6 max-w-5xl space-y-5">
      {error ? <div role="alert" aria-label="Không thể lưu bệnh nhân" ref={errorSummary} tabIndex={-1} className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><p>{error.message}</p>{error.correlationId ? <p className="mt-1 font-mono text-xs">Mã theo dõi: {error.correlationId}</p> : null}</div> : null}
      <p className="text-sm text-muted-foreground">Các trường có dấu * là bắt buộc.</p>
      <fieldset disabled={blocked} className="min-w-0 space-y-5">
        <FormSection title="Thông tin cá nhân" description="Thông tin định danh của bệnh nhân. Kiểm tra kỹ CMND/CCCD trước khi lưu.">
          <Field label="Họ tên" error={fieldErrors.hoTen} htmlFor="patient-name" required><input id="patient-name" aria-invalid={Boolean(fieldErrors.hoTen)} aria-describedby={fieldErrors.hoTen ? "patient-name-error" : undefined} autoComplete="name" required maxLength={100} value={form.hoTen} onChange={(event) => set("hoTen", event.target.value)} className={controlClassName} /></Field>
          <Field label="Ngày sinh" error={fieldErrors.ngaySinh} htmlFor="patient-birth-date" required><input id="patient-birth-date" aria-invalid={Boolean(fieldErrors.ngaySinh)} aria-describedby={fieldErrors.ngaySinh ? "patient-birth-date-error" : undefined} required type="date" max={patientToday()} value={form.ngaySinh} onChange={(event) => set("ngaySinh", event.target.value)} className={controlClassName} /></Field>
          <Field label="Giới tính" error={fieldErrors.gioiTinh} htmlFor="patient-gender" required><select id="patient-gender" aria-invalid={Boolean(fieldErrors.gioiTinh)} aria-describedby={fieldErrors.gioiTinh ? "patient-gender-error" : undefined} value={form.gioiTinh} onChange={(event) => set("gioiTinh", event.target.value)} className={controlClassName}><option value="M">Nam</option><option value="F">Nữ</option></select></Field>
          <Field label="Số CMND/CCCD" error={fieldErrors.soCmnd} htmlFor="patient-identity" required><input id="patient-identity" aria-invalid={Boolean(fieldErrors.soCmnd)} aria-describedby={fieldErrors.soCmnd ? "patient-identity-error" : undefined} required maxLength={20} value={form.soCmnd} onChange={(event) => set("soCmnd", event.target.value)} className={controlClassName} /></Field>
        </FormSection>
        <FormSection title="Liên hệ & bảo hiểm" description="Thông tin liên lạc và số bảo hiểm y tế, nếu có.">
          <Field label="Số điện thoại" error={fieldErrors.soDienThoai} htmlFor="patient-phone" hint="10 đến 15 chữ số."><input id="patient-phone" aria-invalid={Boolean(fieldErrors.soDienThoai)} aria-describedby={fieldErrors.soDienThoai ? "patient-phone-error" : "patient-phone-hint"} type="tel" autoComplete="tel" inputMode="numeric" pattern="[0-9]{10,15}" value={form.soDienThoai} onChange={(event) => set("soDienThoai", event.target.value)} className={controlClassName} /></Field>
          <Field label="Email" error={fieldErrors.email} htmlFor="patient-email"><input id="patient-email" aria-invalid={Boolean(fieldErrors.email)} aria-describedby={fieldErrors.email ? "patient-email-error" : undefined} type="email" autoComplete="email" maxLength={100} value={form.email} onChange={(event) => set("email", event.target.value)} className={controlClassName} /></Field>
          <Field label="Số BHYT" error={fieldErrors.bhytSo} htmlFor="patient-insurance" hint="Định dạng: 2 số-8 số-1 số."><input id="patient-insurance" aria-invalid={Boolean(fieldErrors.bhytSo)} aria-describedby={fieldErrors.bhytSo ? "patient-insurance-error" : "patient-insurance-hint"} pattern="[0-9]{2}-[0-9]{8}-[0-9]" value={form.bhytSo} onChange={(event) => set("bhytSo", event.target.value)} className={controlClassName} /></Field>
          <Field label="Địa chỉ" error={fieldErrors.diaChi} htmlFor="patient-address"><textarea id="patient-address" aria-invalid={Boolean(fieldErrors.diaChi)} aria-describedby={fieldErrors.diaChi ? "patient-address-error" : undefined} autoComplete="street-address" maxLength={255} rows={3} value={form.diaChi} onChange={(event) => set("diaChi", event.target.value)} className={controlClassName} /></Field>
        </FormSection>
      </fieldset>
      <div className="flex flex-wrap justify-end gap-3 border-t border-border pt-5"><Button type="button" disabled={saving} onClick={() => { if (confirmLeave()) router.back(); }}>Quay lại</Button><Button type="submit" variant="primary" loading={saving} disabled={blocked}>{editing ? "Lưu thay đổi" : "Tạo bệnh nhân"}</Button></div>
    </form>
  );
}
