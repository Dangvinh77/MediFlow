"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { patientApi } from "../api";
import type { CreatePatientRequest, GioiTinh } from "../types";

interface PatientFormProps { patientId?: string }
interface FormState {
  hoTen: string; ngaySinh: string; gioiTinh: GioiTinh; soCmnd: string;
  diaChi: string; soDienThoai: string; email: string; bhytSo: string;
}
const emptyForm: FormState = { hoTen: "", ngaySinh: "", gioiTinh: "M", soCmnd: "", diaChi: "", soDienThoai: "", email: "", bhytSo: "" };

export function PatientForm({ patientId }: PatientFormProps) {
  const router = useRouter();
  const editing = Boolean(patientId);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [loading, setLoading] = useState(editing);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    if (!patientId) return;
    let active = true;
    const timer = window.setTimeout(() => {
      setLoading(true); setError(null);
      patientApi.getById(patientId).then((patient) => {
        if (!active) return;
        setForm({ hoTen: patient.hoTen, ngaySinh: patient.ngaySinh, gioiTinh: patient.gioiTinh, soCmnd: patient.soCmnd, diaChi: patient.diaChi ?? "", soDienThoai: patient.soDienThoai ?? "", email: patient.email ?? "", bhytSo: patient.bhytSo ?? "" });
      }).catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
        else setError({ message: cause instanceof Error ? cause.message : "Không thể tải bệnh nhân.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
      }).finally(() => { if (active) setLoading(false); });
    }, 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, [patientId, retryToken, router]);

  function set<K extends keyof FormState>(key: K, value: FormState[K]) { setForm((current) => ({ ...current, [key]: value })); }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true); setError(null);
    const shared = { hoTen: form.hoTen.trim(), ngaySinh: form.ngaySinh, gioiTinh: form.gioiTinh, diaChi: form.diaChi.trim() || null, soDienThoai: form.soDienThoai.trim() || null, email: form.email.trim() || null, bhytSo: form.bhytSo.trim() || null };
    try {
      const patient = patientId
        ? await patientApi.update(patientId, shared)
        : await patientApi.create({ ...shared, soCmnd: form.soCmnd.trim() } satisfies CreatePatientRequest);
      router.push(`/patients/${patient.maBenhNhan}?notice=${editing ? "updated" : "created"}`);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setError({ message: cause instanceof Error ? cause.message : "Không thể lưu bệnh nhân.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    } finally { setSaving(false); }
  }

  if (loading) return <AsyncState kind="loading" message="Đang tải thông tin bệnh nhân…" />;
  if (error && editing && !form.hoTen) return <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => setRetryToken((value) => value + 1)} />;

  return (
    <form onSubmit={submit} className="mt-6 space-y-6">
      {error ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><p>{error.message}</p>{error.correlationId ? <p className="mt-1 font-mono text-xs">Mã theo dõi: {error.correlationId}</p> : null}</div> : null}
      <div className="grid gap-5 rounded-xl border border-border bg-surface p-6 md:grid-cols-2">
        <Field label="Họ tên" htmlFor="patient-name" required><input id="patient-name" required maxLength={100} value={form.hoTen} onChange={(event) => set("hoTen", event.target.value)} className={controlClassName} /></Field>
        <Field label="Ngày sinh" htmlFor="patient-birth-date" required><input id="patient-birth-date" required type="date" max={new Date().toISOString().slice(0, 10)} value={form.ngaySinh} onChange={(event) => set("ngaySinh", event.target.value)} className={controlClassName} /></Field>
        <Field label="Giới tính" htmlFor="patient-gender" required><select id="patient-gender" value={form.gioiTinh} onChange={(event) => set("gioiTinh", event.target.value as GioiTinh)} className={controlClassName}><option value="M">Nam</option><option value="F">Nữ</option></select></Field>
        <Field label="Số CMND/CCCD" htmlFor="patient-identity" required hint={editing ? "Số định danh không thể đổi sau khi tạo." : undefined}><input id="patient-identity" required={!editing} disabled={editing} maxLength={20} value={form.soCmnd} onChange={(event) => set("soCmnd", event.target.value)} className={controlClassName} /></Field>
        <Field label="Số điện thoại" htmlFor="patient-phone" hint="10 đến 15 chữ số."><input id="patient-phone" inputMode="numeric" pattern="[0-9]{10,15}" value={form.soDienThoai} onChange={(event) => set("soDienThoai", event.target.value)} className={controlClassName} /></Field>
        <Field label="Email" htmlFor="patient-email"><input id="patient-email" type="email" maxLength={100} value={form.email} onChange={(event) => set("email", event.target.value)} className={controlClassName} /></Field>
        <Field label="Số BHYT" htmlFor="patient-insurance" hint="Định dạng: 2 số-8 số-1 số."><input id="patient-insurance" pattern="[0-9]{2}-[0-9]{8}-[0-9]" value={form.bhytSo} onChange={(event) => set("bhytSo", event.target.value)} className={controlClassName} /></Field>
        <Field label="Địa chỉ" htmlFor="patient-address"><textarea id="patient-address" maxLength={255} rows={3} value={form.diaChi} onChange={(event) => set("diaChi", event.target.value)} className={controlClassName} /></Field>
      </div>
      <div className="flex flex-wrap gap-2"><Button type="submit" variant="primary" loading={saving}>{editing ? "Lưu thay đổi" : "Tạo bệnh nhân"}</Button><Button type="button" disabled={saving} onClick={() => router.back()}>Quay lại</Button></div>
    </form>
  );
}
