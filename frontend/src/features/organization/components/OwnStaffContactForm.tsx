"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/auth";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { organizationApi } from "../api";
import type { StaffDTO } from "../types";
import { useUnsavedChanges } from "../use-unsaved-changes";
import { FormSection } from "./RecordLayout";

export function OwnStaffContactForm({ staff, onSaved }: { staff: StaffDTO; onSaved: (staff: StaffDTO) => void }) {
  const router = useRouter();
  const { markDirty, markSaved } = useUnsavedChanges();
  const [phone, setPhone] = useState(staff.phoneNumber ?? "");
  const [email, setEmail] = useState(staff.email ?? "");
  const [address, setAddress] = useState(staff.address ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const alive = useRef(true), lock = useRef(false);
  const requestVersion = useRef(0);
  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; requestVersion.current += 1; };
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (lock.current) return;
    lock.current = true;
    const version = ++requestVersion.current;
    const session = getSessionKey();
    const current = () => alive.current && version === requestVersion.current && session === getSessionKey();
    setBusy(true); setError(null); setDone(false);
    try {
      const updated = await organizationApi.updateOwnContact(staff.staffId, {
        phoneNumber: phone.trim() || null, email: email.trim() || null, address: address.trim() || null,
      });
      if (!current()) return;
      markSaved(); onSaved(updated); setDone(true);
    } catch (cause: unknown) {
      if (!current()) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setError(cause instanceof Error ? cause.message : "Không thể lưu thông tin liên hệ. Hãy kiểm tra lại hồ sơ trước khi thử lại.");
    } finally {
      if (current()) { lock.current = false; setBusy(false); }
    }
  }

  return <form onSubmit={submit} onChangeCapture={() => { markDirty(); setDone(false); }} className="space-y-4">
    <fieldset disabled={busy || !staff.active}>
      <FormSection title="Cập nhật liên hệ của bạn" description="Bạn có thể sửa email, điện thoại và địa chỉ. Thông tin định danh và công tác do quản trị viên cập nhật.">
        <Field label="Điện thoại" htmlFor="own-staff-phone" hint="10 đến 15 chữ số."><input id="own-staff-phone" type="tel" autoComplete="tel" pattern="[0-9]{10,15}" maxLength={15} value={phone} onChange={event => setPhone(event.target.value)} className={controlClassName} /></Field>
        <Field label="Email" htmlFor="own-staff-email"><input id="own-staff-email" type="email" autoComplete="email" maxLength={100} value={email} onChange={event => setEmail(event.target.value)} className={controlClassName} /></Field>
        <Field label="Địa chỉ" htmlFor="own-staff-address"><input id="own-staff-address" autoComplete="street-address" maxLength={255} value={address} onChange={event => setAddress(event.target.value)} className={controlClassName} /></Field>
      </FormSection>
      <div className="mt-4 flex flex-wrap items-center justify-end gap-3">
        {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
        {done ? <p role="status" className="text-sm text-success">Đã cập nhật thông tin liên hệ.</p> : null}
        <Button type="submit" variant="primary" loading={busy}>Lưu thông tin liên hệ</Button>
      </div>
    </fieldset>
  </form>;
}
