"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { ReferenceInput } from "@/components/references/ReferenceInput";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { useCommand } from "@/lib/use-command";
import { isUuid } from "@/lib/validation";
import { billingApi } from "../api";

// The native date input's displayed order follows the browser/OS locale, not this code.
// Read that same locale back so the hint always matches what the picker above actually shows.
const dateInputOrderHint = new Intl.DateTimeFormat(undefined, { year: "numeric", month: "2-digit", day: "2-digit" })
  .formatToParts(new Date(2024, 0, 13))
  .filter((part) => part.type === "day" || part.type === "month" || part.type === "year")
  .map((part) => (part.type === "day" ? "ngày" : part.type === "month" ? "tháng" : "năm"))
  .join("-");

export function InvoiceForm() {
  const { localize } = useLocale();
  const router = useRouter(); const [patientId, setPatientId] = useState(""); const [createdDate, setCreatedDate] = useState(new Date().toISOString().slice(0, 10)); const command = useCommand(); const saving = command.busy; const [error, setError] = useState<string | null>(null);
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const id = patientId.trim(); if (!isUuid(id)) { setError("Mã bệnh nhân phải là UUID hợp lệ."); return; } setError(null); void command.run(() => billingApi.create({ patientId: id, createdDate }), (invoice) => router.push(`/billing/${invoice.invoiceId}?notice=created`)); }

  return <form onSubmit={submit} className="mt-6 max-w-2xl space-y-5 rounded-xl border border-border bg-surface p-6"><CommandFeedback error={command.error} uncertain={command.uncertain} /><L10nValue>{command.uncertain ? <p className="text-sm"><L10n>{"Tra cứu hóa đơn của bệnh nhân trước khi lập yêu cầu mới."}</L10n></p> : null}</L10nValue><L10nValue>{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><L10nValue>{error}</L10nValue></p> : null}</L10nValue><Field label={localize("Mã bệnh nhân")} htmlFor="invoice-patient" required><ReferenceInput kind="patient" id="invoice-patient" required value={patientId} onValueChange={(selectedId) => setPatientId(selectedId)} className={controlClassName} /></Field><Field label={localize("Ngày lập")} htmlFor="invoice-date" required hint={`Ô lịch phía trên nhập theo thứ tự: ${dateInputOrderHint}.`}><input id="invoice-date" required type="date" max={new Date().toISOString().slice(0, 10)} value={createdDate} onChange={(event) => setCreatedDate(event.target.value)} className={controlClassName} /></Field><p className="text-sm text-muted-foreground"><L10n>{"Server tự cộng toàn bộ khoản phí chưa thanh toán; giao diện không nhận số tiền nhập tay."}</L10n></p><div className="flex gap-2"><Button type="submit" variant="primary" loading={saving} disabled={command.uncertain}><L10n>{"Lập hóa đơn"}</L10n></Button><Button type="button" disabled={saving} onClick={() => router.back()}><L10n>{"Quay lại"}</L10n></Button></div></form>;
}
