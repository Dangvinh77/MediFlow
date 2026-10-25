"use client";

import { ReferenceInput } from "@/components/references/ReferenceInput";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { notificationApi } from "../api";
import type { NotificationChannel } from "../types";
import { toSendNotificationRequest, validateSendNotification, type SendNotificationErrors, type SendNotificationValues } from "../sendNotificationValidation";
import { NotificationAccess, useNotificationRequestScope } from "./NotificationAccess";

export function SendNotificationForm() {
  return <NotificationAccess permission="send"><SendForm /></NotificationAccess>;
}

const emptyValues: SendNotificationValues = { patientId: "", title: "", content: "", channel: "IN_APP", recipientAddress: "" };

function SendForm() {
  const router = useRouter();
  const isCurrent = useNotificationRequestScope("send");
  const [values, setValues] = useState<SendNotificationValues>(emptyValues);
  const [errors, setErrors] = useState<SendNotificationErrors>({});
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  const completed = useRef(false);
  const [sent, setSent] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const focusTarget = useRef<string | null>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const locked = saving || uncertain || sent || forbidden;
  useEffect(() => {
    if (saving) return;
    if (focusTarget.current && !locked) document.getElementById(`notification-${focusTarget.current}`)?.focus();
    else if (error) feedbackRef.current?.focus();
    focusTarget.current = null;
  }, [errors, saving, error, locked]);

  function update(name: keyof SendNotificationValues, value: string) {
    if (locked) return;
    setValues(current => ({ ...current, [name]: value, ...(name === "channel" ? { recipientAddress: "" } : {}) }));
    setErrors(current => ({ ...current, [name]: undefined, ...(name === "channel" ? { recipientAddress: undefined } : {}) }));
    setError(null);
  }
  function focusError(next: SendNotificationErrors) {
    focusTarget.current = (["patientId", "title", "content", "channel", "recipientAddress"] as const)
      .find(name => next[name] && (name !== "recipientAddress" || values.channel !== "IN_APP")) ?? null;
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current || completed.current || uncertain || forbidden || !isCurrent()) return;
    const next = validateSendNotification(values);
    setErrors(next); setError(null);
    if (Object.keys(next).length) { focusError(next); return; }
    pending.current = true; setSaving(true);
    try {
      const notification = await notificationApi.send(toSendNotificationRequest(values));
      if (isCurrent()) {
        completed.current = true; setSent(true);
        router.push(`/notifications/${notification.notificationId}?notice=sent`);
      }
    } catch (cause: unknown) {
      if (!isCurrent()) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      const suffix = cause instanceof ApiRequestError && cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
      if (cause instanceof ApiRequestError && cause.status === 403) setForbidden(true);
      const unknown = !(cause instanceof ApiRequestError) || cause.status === 408 || cause.status >= 500 || cause.status < 400 || cause.status === 409;
      setUncertain(unknown);
      if (unknown) {
        // This endpoint has no idempotency contract. Never offer a blind resend.
        setError(`Chưa xác định được kết quả gửi. Hãy tra cứu lịch sử bệnh nhân trước khi tạo thông báo mới.${suffix}`);
      } else {
        const fields: SendNotificationErrors = {};
        for (const detail of cause.details) {
          if (detail.field in emptyValues) fields[detail.field as keyof SendNotificationValues] = detail.message;
        }
        focusError(fields); setErrors(fields);
        setError(`${cause.status === 403 ? "Bạn không có quyền gửi thông báo." : cause.message}${suffix}`);
      }
    } finally {
      pending.current = false;
      if (isCurrent()) setSaving(false);
    }
  }
  const field = (name: keyof SendNotificationValues) => ({
    id: `notification-${name}`, disabled: locked, "aria-required": true,
    "aria-invalid": errors[name] ? true : undefined,
    "aria-describedby": errors[name] ? `notification-${name}-error` : undefined,
    className: controlClassName,
  });
  return (
    <form onSubmit={submit} noValidate aria-busy={saving || undefined} className="mt-6 max-w-3xl space-y-5 rounded-xl border border-border bg-surface p-6">
      {error && <p ref={feedbackRef} role="alert" tabIndex={-1} className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger">{error}</p>}
      <Field label="Bệnh nhân" htmlFor="notification-patientId" error={errors.patientId} required><ReferenceInput kind="patient" {...field("patientId")} value={values.patientId} onValueChange={(selectedId) => update("patientId", selectedId)} placeholder="Tìm bệnh nhân theo tên" /></Field>
      <Field label="Tiêu đề" htmlFor="notification-title" error={errors.title} required><input {...field("title")} maxLength={255} value={values.title} onChange={event => update("title", event.target.value)} /></Field>
      <Field label="Nội dung" htmlFor="notification-content" error={errors.content} required><textarea {...field("content")} rows={6} value={values.content} onChange={event => update("content", event.target.value)} /></Field>
      <div className="grid gap-4 md:grid-cols-2">
        <Field label="Kênh" htmlFor="notification-channel" error={errors.channel} required><select {...field("channel")} value={values.channel} onChange={event => update("channel", event.target.value as NotificationChannel)}><option value="IN_APP">Trong ứng dụng</option><option value="EMAIL">Email</option><option value="SMS">SMS</option></select></Field>
        <Field label="Địa chỉ người nhận" htmlFor="notification-recipientAddress" error={errors.recipientAddress} required={values.channel !== "IN_APP"} hint={values.channel === "IN_APP" ? "Không cần cho thông báo trong ứng dụng." : values.channel === "SMS" ? "Số điện thoại bắt đầu bằng 0, gồm 10–11 chữ số." : "Email nhận thông báo."}>
          <input {...field("recipientAddress")} disabled={locked || values.channel === "IN_APP"} aria-required={values.channel !== "IN_APP"} maxLength={150} type={values.channel === "EMAIL" ? "email" : "text"} inputMode={values.channel === "SMS" ? "tel" : undefined} value={values.recipientAddress} onChange={event => update("recipientAddress", event.target.value)} autoComplete="off" />
        </Field>
      </div>
      <div className="flex flex-wrap gap-3">
        <Button type="submit" variant="primary" loading={saving} disabled={uncertain || sent || forbidden}>{sent ? "Đã tiếp nhận yêu cầu" : "Gửi thông báo"}</Button>
        <Button type="button" disabled={saving} onClick={() => router.push("/notifications")}>{uncertain ? "Tra cứu lịch sử bệnh nhân" : "Quay lại tra cứu"}</Button>
      </div>
    </form>
  );
}
