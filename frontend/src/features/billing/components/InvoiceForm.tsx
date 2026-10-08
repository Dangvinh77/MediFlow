"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { isUuid } from "@/lib/validation";
import { billingApi } from "../api";

export function InvoiceForm() {
  const router = useRouter(); const [patientId, setPatientId] = useState(""); const [createdDate, setCreatedDate] = useState(new Date().toISOString().slice(0, 10)); const [saving, setSaving] = useState(false); const [error, setError] = useState<string | null>(null);
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const id = patientId.trim(); if (!isUuid(id)) { setError("Mã bệnh nhân phải là UUID hợp lệ."); return; } setSaving(true); setError(null); try { const invoice = await billingApi.create({ patientId: id, createdDate }); router.push(`/billing/${invoice.invoiceId}?notice=created`); } catch (cause: unknown) { setError(cause instanceof Error ? cause.message : "Không thể lập hóa đơn."); } finally { setSaving(false); } }
  return <form onSubmit={submit} className="mt-6 max-w-2xl space-y-5 rounded-xl border border-border bg-surface p-6">{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}<Field label="Mã bệnh nhân" htmlFor="invoice-patient" required><input id="invoice-patient" required value={patientId} onChange={(event) => setPatientId(event.target.value)} className={controlClassName} /></Field><Field label="Ngày lập" htmlFor="invoice-date" required><input id="invoice-date" required type="date" max={new Date().toISOString().slice(0, 10)} value={createdDate} onChange={(event) => setCreatedDate(event.target.value)} className={controlClassName} /></Field><p className="text-sm text-muted-foreground">Server tự cộng toàn bộ khoản phí chưa thanh toán; giao diện không nhận số tiền nhập tay.</p><div className="flex gap-2"><Button type="submit" variant="primary" loading={saving}>Lập hóa đơn</Button><Button type="button" disabled={saving} onClick={() => router.back()}>Quay lại</Button></div></form>;
}
