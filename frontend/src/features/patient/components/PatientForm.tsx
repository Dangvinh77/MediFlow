"use client";

import { useRouter } from "next/navigation";
import { useSyncExternalStore, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { getToken, subscribeToAuthChanges } from "@/lib/session";
import { patientToday } from "../logic";
import { usePatientForm } from "../usePatientForm";

interface PatientFormProps { patientId?: string }
const getServerSession = () => null;

export function PatientForm({ patientId }: PatientFormProps) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getToken, getServerSession);
  return <PatientFormWorkspace key={`${patientId ?? "create"}:${session}`} patientId={patientId} />;
}

function PatientFormWorkspace({ patientId }: PatientFormProps) {
  const router = useRouter();
  const editing = Boolean(patientId);
  const { form, set, loading, loaded, saving, blocked, error, save, retry } = usePatientForm(patientId);
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); void save(); }
  if (loading) return <AsyncState kind="loading" message="Đang tải thông tin bệnh nhân…" />;
  if (error && editing && !loaded) return <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={retry} />;

  return (
    <form onSubmit={submit} className="mt-6 space-y-6">
      {error ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><p>{error.message}</p>{error.correlationId ? <p className="mt-1 font-mono text-xs">Mã theo dõi: {error.correlationId}</p> : null}</div> : null}
      <div className="grid gap-5 rounded-xl border border-border bg-surface p-6 md:grid-cols-2">
        <Field label="Họ tên" htmlFor="patient-name" required><input id="patient-name" required maxLength={100} value={form.hoTen} onChange={(event) => set("hoTen", event.target.value)} className={controlClassName} /></Field>
        <Field label="Ngày sinh" htmlFor="patient-birth-date" required><input id="patient-birth-date" required type="date" max={patientToday()} value={form.ngaySinh} onChange={(event) => set("ngaySinh", event.target.value)} className={controlClassName} /></Field>
        <Field label="Giới tính" htmlFor="patient-gender" required><select id="patient-gender" value={form.gioiTinh} onChange={(event) => set("gioiTinh", event.target.value)} className={controlClassName}><option value="M">Nam</option><option value="F">Nữ</option></select></Field>
        <Field label="Số CMND/CCCD" htmlFor="patient-identity" required><input id="patient-identity" required maxLength={20} value={form.soCmnd} onChange={(event) => set("soCmnd", event.target.value)} className={controlClassName} /></Field>
        <Field label="Số điện thoại" htmlFor="patient-phone" hint="10 đến 15 chữ số."><input id="patient-phone" inputMode="numeric" pattern="[0-9]{10,15}" value={form.soDienThoai} onChange={(event) => set("soDienThoai", event.target.value)} className={controlClassName} /></Field>
        <Field label="Email" htmlFor="patient-email"><input id="patient-email" type="email" maxLength={100} value={form.email} onChange={(event) => set("email", event.target.value)} className={controlClassName} /></Field>
        <Field label="Số BHYT" htmlFor="patient-insurance" hint="Định dạng: 2 số-8 số-1 số."><input id="patient-insurance" pattern="[0-9]{2}-[0-9]{8}-[0-9]" value={form.bhytSo} onChange={(event) => set("bhytSo", event.target.value)} className={controlClassName} /></Field>
        <Field label="Địa chỉ" htmlFor="patient-address"><textarea id="patient-address" maxLength={255} rows={3} value={form.diaChi} onChange={(event) => set("diaChi", event.target.value)} className={controlClassName} /></Field>
      </div>
      <div className="flex flex-wrap gap-2"><Button type="submit" variant="primary" loading={saving} disabled={blocked}>{editing ? "Lưu thay đổi" : "Tạo bệnh nhân"}</Button><Button type="button" disabled={saving} onClick={() => router.back()}>Quay lại</Button></div>
    </form>
  );
}
