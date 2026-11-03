"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { useCommand } from "@/lib/use-command";
import { billingApi } from "../api";
import type { LedgerPaymentMethod, LedgerRefundDTO, OutpatientAdjustmentDTO, PrescriptionAdjustmentDTO, RefundDueDTO } from "../types";
import { BillingSessionBoundary } from "./BillingSessionBoundary";

type RefundSourceType = "LAB_TEST" | "PRESCRIPTION";
type AdjustmentDTO = OutpatientAdjustmentDTO | PrescriptionAdjustmentDTO;

function readRefundObligations(sourceType: RefundSourceType, sourceId: string) {
  return sourceType === "LAB_TEST" ? billingApi.labRefundsDue(sourceId) : billingApi.refundsDue(sourceId);
}

export function RefundDueLookup() {
  return <BillingSessionBoundary identity="refund-due"><RefundDueLookupSession /></BillingSessionBoundary>;
}

function RefundDueLookupSession() {
  const [sourceType, setSourceType] = useState<RefundSourceType>("PRESCRIPTION");
  const [id, setId] = useState("");
  const [result, setResult] = useState<AdjustmentDTO | null>(null);
  const [lookupOpen, setLookupOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [commandLocked, setCommandLocked] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [receipts, setReceipts] = useState<LedgerRefundDTO[]>([]);
  const [revision, setRevision] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  const focusLookup = useRef(false);
  const locked = useRef(false);
  const generation = useRef(0);
  const sourceIdLabel = sourceType === "LAB_TEST" ? "Mã xét nghiệm" : "Mã đơn thuốc";

  useEffect(() => () => { generation.current++; }, []);
  useEffect(() => {
    if (result === null && focusLookup.current) {
      focusLookup.current = false;
      input.current?.focus();
    }
  }, [result]);

  function lockCommand(value: boolean) {
    locked.current = value;
    setCommandLocked(value);
  }

  function beginRefund() {
    if (locked.current || busy || refreshError) return false;
    lockCommand(true);
    return true;
  }

  function rejectedRefund(cause: unknown) {
    if (cause instanceof ApiRequestError && cause.status >= 400 && cause.status < 500 && cause.status !== 408) lockCommand(false);
  }

  async function refresh() {
    const request = ++generation.current;
    setBusy(true);
    setRefreshError(null);
    try {
      const data = await readRefundObligations(sourceType, id.trim());
      if (request !== generation.current) return;
      setResult(data);
      setRevision((value) => value + 1);
    } catch (cause) {
      if (request !== generation.current) return;
      setRefreshError(cause instanceof ApiRequestError ? `${cause.message}${cause.correlationId ? ` — Mã theo dõi: ${cause.correlationId}` : ""}` : "Không thể cập nhật nghĩa vụ hoàn tiền.");
    } finally {
      if (request === generation.current) { setBusy(false); lockCommand(false); }
    }
  }

  function refunded(refund: LedgerRefundDTO) {
    if (refund.status !== "COMPLETED") return;
    setReceipts((values) => [...values, refund]);
    void refresh();
  }

  function anotherSource() {
    if (locked.current || busy) return;
    generation.current++;
    focusLookup.current = true;
    setId(""); setResult(null); setError(null); setRefreshError(null); setReceipts([]); setLookupOpen(true);
  }

  async function lookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || locked.current || result !== null) return;
    setResult(null);
    if (!isUuid(id.trim())) { setError(`${sourceIdLabel} không hợp lệ.`); return; }
    const request = ++generation.current;
    setBusy(true); setError(null);
    try {
      const data = await readRefundObligations(sourceType, id.trim());
      if (request === generation.current) { setResult(data); setLookupOpen(false); }
    } catch (cause) {
      if (request === generation.current) setError(cause instanceof ApiRequestError ? `${cause.message}${cause.correlationId ? ` — Mã theo dõi: ${cause.correlationId}` : ""}` : "Không thể tra cứu nghĩa vụ hoàn tiền.");
    } finally {
      if (request === generation.current) setBusy(false);
    }
  }

  return <section aria-label="Nghĩa vụ hoàn tiền" className="space-y-4">
    <p className="text-sm text-muted-foreground">Tra cứu nghĩa vụ do Billing xác nhận sau khi hủy hoặc điều chỉnh xét nghiệm, đơn thuốc. Chỉ ghi nhận khoản đã thực tế hoàn cho người bệnh.</p>
    <details open={lookupOpen} onToggle={(event) => setLookupOpen(event.currentTarget.open)} className="max-w-xl rounded-lg border border-border p-3">
      <summary className="min-h-9 cursor-pointer text-sm font-medium">Tra cứu khoản cần hoàn (hỗ trợ)</summary>
      <form onSubmit={lookup} noValidate className="mt-3 space-y-3">
        <Field label="Loại nguồn hoàn tiền" htmlFor="refund-source-type" required>
          <select id="refund-source-type" value={sourceType} disabled={busy || commandLocked || result !== null} onChange={(event) => { setSourceType(event.target.value as RefundSourceType); setId(""); setError(null); }} className={controlClassName}>
            <option value="LAB_TEST">Xét nghiệm</option>
            <option value="PRESCRIPTION">Đơn thuốc</option>
          </select>
        </Field>
        <Field label={sourceIdLabel} htmlFor="refund-source-id" required>
          <input ref={input} id="refund-source-id" value={id} disabled={busy || result !== null} onChange={(event) => setId(event.target.value)} className={controlClassName} />
        </Field>
        <Button type="submit" loading={busy} disabled={result !== null}>Tra cứu nghĩa vụ hoàn tiền</Button>
      </form>
    </details>
    {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
    {receipts.map((receipt) => <div key={receipt.refundTransactionId} role="status" className="space-y-2 rounded-lg border border-border p-4">
      <p>Đã ghi nhận hoàn tiền {formatDecimal(receipt.amount)} {receipt.currency}.</p>
      <details>
        <summary className="min-h-8 cursor-pointer text-sm text-muted-foreground">Thông tin biên nhận</summary>
        <dl className="mt-2 space-y-2 break-all text-sm"><Info label="Mã giao dịch hoàn" value={receipt.refundTransactionId} /><Info label="Giao dịch gốc" value={receipt.originalTransactionId} /></dl>
      </details>
    </div>)}
    {result ? <div className="space-y-4">
      <p className="text-sm">Trạng thái điều chỉnh: {result.status}{result.rejectionCode ? ` — ${result.rejectionCode}` : ""}</p>
      {busy ? <p role="status">Đang cập nhật nghĩa vụ hoàn tiền…</p> : null}
      {refreshError ? <div className="space-y-2"><p role="alert" className="text-sm text-danger">Đã giữ biên nhận hoàn tiền. Chưa cập nhật được nghĩa vụ: {refreshError}</p><Button type="button" onClick={() => void refresh()}>Tải lại nghĩa vụ hoàn tiền</Button></div> : null}
      {result.refundsDue.length === 0 ? <p role="status">Không có nghĩa vụ hoàn tiền trong kết quả này.</p> : result.refundsDue.map((due) => <RefundForm key={`${due.originalTransactionId}:${revision}`} due={due} blocked={commandLocked || busy || refreshError !== null} onStart={beginRefund} onRejected={rejectedRefund} onRefunded={refunded} />)}
      <Button type="button" disabled={busy || commandLocked} onClick={anotherSource}>Tra cứu nguồn khác</Button>
      {commandLocked ? <p className="text-xs text-muted-foreground">Hoàn tất xử lý và đối soát giao dịch trước khi tra cứu nguồn khác.</p> : null}
    </div> : null}
  </section>;
}

function RefundForm({ due, blocked, onStart, onRejected, onRefunded }: {
  due: RefundDueDTO;
  blocked: boolean;
  onStart: () => boolean;
  onRejected: (cause: unknown) => void;
  onRefunded: (refund: LedgerRefundDTO) => void;
}) {
  const command = useCommand();
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [method, setMethod] = useState<LedgerPaymentMethod>("CASH");
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<LedgerRefundDTO | null>(null);
  const disabled = blocked || command.busy || command.uncertain || result !== null;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!confirmed || disabled) return;
    const cents = Math.round(Number(amount) * 100);
    if (!/^\d+(?:\.\d{1,2})?$/.test(amount.trim()) || !Number.isSafeInteger(cents) || cents <= 0 || cents > Math.round(due.amount * 100) || !reason.trim() || reason.trim().length > 500) {
      setError("Nhập số tiền đã hoàn hợp lệ, không vượt nghĩa vụ, tối đa 2 chữ số thập phân và lý do tối đa 500 ký tự."); return;
    }
    if (!onStart()) return;
    setError(null);
    void command.run(async () => {
      try { return await billingApi.recordRefund(due.originalTransactionId, { idempotencyKey: crypto.randomUUID(), amount: cents / 100, reason: reason.trim(), paymentMethod: method }); }
      catch (cause) { onRejected(cause); throw cause; }
    }, (refund) => { setResult(refund); onRefunded(refund); });
  }

  return <form onSubmit={submit} noValidate className="max-w-xl space-y-4 border-l-2 border-primary/30 pl-4">
    <p>Nghĩa vụ theo Billing: <strong>{formatDecimal(due.amount)} {due.currency}</strong></p>
    <p className="text-sm text-muted-foreground">Chỉ ghi nhận tiền đã thực tế hoàn cho người bệnh. Thao tác này không chuyển tiền qua ngân hàng.</p>
    <CommandFeedback error={command.error} uncertain={command.uncertain} />
    {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
    {result && result.status !== "COMPLETED" ? <p role="status">Chưa xác nhận hoàn tất; cần đối soát giao dịch trước khi tiếp tục.</p> : null}
    <Field label="Số tiền đã hoàn" htmlFor={`refund-amount-${due.originalTransactionId}`} required><input id={`refund-amount-${due.originalTransactionId}`} inputMode="decimal" value={amount} disabled={disabled} onChange={(event) => setAmount(event.target.value)} className={controlClassName} /></Field>
    <Field label="Lý do" htmlFor={`refund-reason-${due.originalTransactionId}`} required><textarea id={`refund-reason-${due.originalTransactionId}`} maxLength={500} value={reason} disabled={disabled} onChange={(event) => setReason(event.target.value)} className={controlClassName} /></Field>
    <Field label="Hình thức hoàn" htmlFor={`refund-method-${due.originalTransactionId}`}><select id={`refund-method-${due.originalTransactionId}`} disabled={disabled} value={method} onChange={(event) => setMethod(event.target.value as LedgerPaymentMethod)} className={controlClassName}><option value="CASH">Tiền mặt</option><option value="TRANSFER">Chuyển khoản</option></select></Field>
    <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={confirmed} disabled={disabled} onChange={(event) => setConfirmed(event.target.checked)} />Tôi đã hoàn tiền và đối soát giao dịch gốc.</label>
    <Button type="submit" variant="primary" loading={command.busy} disabled={!confirmed || disabled}>Ghi nhận hoàn tiền</Button>
    <details>
      <summary className="min-h-8 cursor-pointer text-sm text-muted-foreground">Thông tin đối soát</summary>
      <dl className="mt-2 space-y-2 break-all text-sm"><Info label="Giao dịch gốc" value={due.originalTransactionId} />{result ? <Info label="Mã giao dịch hoàn" value={result.refundTransactionId} /> : null}</dl>
    </details>
  </form>;
}

function Info({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs text-muted-foreground">{label}</dt><dd className="font-mono">{value}</dd></div>;
}
