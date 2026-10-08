"use client";

import { useState, useSyncExternalStore, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { isUuid } from "@/lib/validation";
import { labApi } from "../api";
import type { LabResultInput, LabTestDTO } from "../types";
import { LabMutationError } from "./LabMutationError";

interface LabActionsProps { test: LabTestDTO; onUpdated: (test: LabTestDTO) => void }
interface RequestError { status: number | null; message: string; correlationId: string | null }
const getServerRole = () => null;
const emptyResult = (): LabResultInput => ({ indicator: "", value: "", unit: null, referenceRange: null });

function requestError(cause: unknown): RequestError {
  return cause instanceof ApiRequestError
    ? { status: cause.status, message: cause.message, correlationId: cause.correlationId }
    : { status: null, message: cause instanceof Error ? cause.message : "Không thể cập nhật xét nghiệm.", correlationId: null };
}

export function LabActions({ test, onUpdated }: LabActionsProps) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState<"results" | "cancel" | null>(null);
  const [error, setError] = useState<RequestError | null>(null);
  const [results, setResults] = useState<LabResultInput[]>([emptyResult()]);
  const [conclusion, setConclusion] = useState("");
  const [performedDate, setPerformedDate] = useState(new Date().toISOString().slice(0, 10));
  const [cancelledBy, setCancelledBy] = useState("");
  const [cancelReason, setCancelReason] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);

  const canWork = role === "ADMIN" || role === "LAB_TECH";
  const canCancel = role === "ADMIN" || role === "DOCTOR" || role === "LAB_TECH";
  const canStart = canWork && (test.status === "PENDING" || test.status === "READY");
  const canEnterResults = canWork && test.status === "IN_PROGRESS";
  const canCancelCurrent = canCancel && !["COMPLETED", "CANCELLED"].includes(test.status);
  if (!canStart && !canEnterResults && !canCancelCurrent) return null;

  async function start() {
    setBusy(true); setError(null); setValidationError(null);
    try { onUpdated(await labApi.start(test.testId)); }
    catch (cause: unknown) { setError(requestError(cause)); }
    finally { setBusy(false); }
  }

  async function submitResults(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = results.map((item) => ({
      indicator: item.indicator.trim(), value: item.value.trim(),
      unit: item.unit?.trim() || null, referenceRange: item.referenceRange?.trim() || null,
    }));
    if (normalized.some((item) => !item.indicator || !item.value)) {
      setValidationError("Mỗi chỉ số phải có tên và giá trị."); return;
    }
    if (!performedDate || performedDate < test.requestedDate) {
      setValidationError("Ngày thực hiện phải từ ngày yêu cầu trở đi."); return;
    }
    setBusy(true); setError(null); setValidationError(null);
    try {
      onUpdated(await labApi.addResults(test.testId, { results: normalized, conclusion: conclusion.trim() || null, performedDate }));
      setMode(null);
    } catch (cause: unknown) { setError(requestError(cause)); }
    finally { setBusy(false); }
  }

  async function submitCancel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const staffId = cancelledBy.trim();
    const reason = cancelReason.trim();
    if (!isUuid(staffId)) { setValidationError("Mã nhân viên hủy phải là UUID hợp lệ."); return; }
    if (!reason) { setValidationError("Lý do hủy là bắt buộc."); return; }
    setBusy(true); setError(null); setValidationError(null);
    try {
      onUpdated(await labApi.cancel(test.testId, { cancelledBy: staffId, reason }));
      setMode(null);
    } catch (cause: unknown) { setError(requestError(cause)); }
    finally { setBusy(false); }
  }

  function updateResult(index: number, field: keyof LabResultInput, value: string) {
    setResults((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, [field]: value } : item));
  }

  return (
    <section className="rounded-xl border border-border bg-surface p-6">
      <h2 className="text-lg font-semibold">Thao tác xét nghiệm</h2>
      <p className="mt-1 text-sm text-muted-foreground">Backend kiểm tra lại quyền và chuyển trạng thái; phản hồi 403 vẫn là quyết định cuối cùng.</p>
      <div className="mt-4 flex flex-wrap gap-2">
        {canStart ? <Button type="button" variant="primary" loading={busy} onClick={() => void start()}>Bắt đầu xét nghiệm</Button> : null}
        {canEnterResults ? <Button type="button" variant="primary" disabled={busy} onClick={() => { setMode("results"); setValidationError(null); }}>Nhập kết quả</Button> : null}
        {canCancelCurrent ? <Button type="button" variant="danger" disabled={busy} onClick={() => { setMode("cancel"); setValidationError(null); }}>Hủy xét nghiệm</Button> : null}
      </div>

      {error ? <LabMutationError {...error} /> : null}
      {validationError ? <p role="alert" className="mt-3 text-sm text-danger">{validationError}</p> : null}

      {mode === "results" ? (
        <form onSubmit={submitResults} className="mt-5 space-y-4 border-t border-border pt-5">
          <div className="flex items-center justify-between"><h3 className="font-semibold">Kết quả chỉ số</h3><Button type="button" variant="ghost" onClick={() => setResults((current) => [...current, emptyResult()])}>Thêm chỉ số</Button></div>
          {results.map((result, index) => (
            <div key={index} className="grid gap-3 rounded-lg border border-border p-4 md:grid-cols-4">
              <Field label="Chỉ số" htmlFor={`result-indicator-${index}`} required><input id={`result-indicator-${index}`} maxLength={100} value={result.indicator} onChange={(event) => updateResult(index, "indicator", event.target.value)} className={controlClassName} /></Field>
              <Field label="Giá trị" htmlFor={`result-value-${index}`} required><input id={`result-value-${index}`} maxLength={50} value={result.value} onChange={(event) => updateResult(index, "value", event.target.value)} className={controlClassName} /></Field>
              <Field label="Đơn vị" htmlFor={`result-unit-${index}`}><input id={`result-unit-${index}`} maxLength={20} value={result.unit ?? ""} onChange={(event) => updateResult(index, "unit", event.target.value)} className={controlClassName} /></Field>
              <Field label="Khoảng tham chiếu" htmlFor={`result-range-${index}`}><input id={`result-range-${index}`} maxLength={50} value={result.referenceRange ?? ""} onChange={(event) => updateResult(index, "referenceRange", event.target.value)} className={controlClassName} /></Field>
              {results.length > 1 ? <Button type="button" variant="ghost" className="md:col-span-4 md:justify-self-start" onClick={() => setResults((current) => current.filter((_, itemIndex) => itemIndex !== index))}>Xóa chỉ số</Button> : null}
            </div>
          ))}
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="Ngày thực hiện" htmlFor="lab-performed-date" required><input id="lab-performed-date" type="date" value={performedDate} onChange={(event) => setPerformedDate(event.target.value)} className={controlClassName} /></Field>
            <Field label="Kết luận" htmlFor="lab-conclusion"><textarea id="lab-conclusion" maxLength={4000} rows={3} value={conclusion} onChange={(event) => setConclusion(event.target.value)} className={controlClassName} /></Field>
          </div>
          <div className="flex gap-2"><Button type="submit" variant="primary" loading={busy}>Lưu kết quả</Button><Button type="button" disabled={busy} onClick={() => setMode(null)}>Quay lại</Button></div>
        </form>
      ) : null}

      {mode === "cancel" ? (
        <form onSubmit={submitCancel} className="mt-5 grid gap-4 border-t border-border pt-5 md:grid-cols-2">
          <Field label="Mã nhân viên hủy" htmlFor="lab-cancelled-by" required hint="UUID nhân viên đang thực hiện thao tác."><input id="lab-cancelled-by" value={cancelledBy} onChange={(event) => setCancelledBy(event.target.value)} className={controlClassName} /></Field>
          <Field label="Lý do hủy" htmlFor="lab-cancel-reason" required><textarea id="lab-cancel-reason" maxLength={1000} rows={3} value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} className={controlClassName} /></Field>
          <div className="flex gap-2 md:col-span-2"><Button type="submit" variant="danger" loading={busy}>Xác nhận hủy</Button><Button type="button" disabled={busy} onClick={() => setMode(null)}>Quay lại</Button></div>
        </form>
      ) : null}
    </section>
  );
}
