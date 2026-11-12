"use client";

import { useEffect, useState, useSyncExternalStore, type FormEvent } from "react";
import { PatientContextBanner } from "@/components/references/PatientContextBanner";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { useCommand } from "@/lib/use-command";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { ApiRequestError } from "@/lib/api";
import { labApi } from "../api";
import type { LabResultInput, LabResultTemplateDTO, LabTestDTO } from "../types";
import { LabTemplateResults, mapTemplateResults, type LabTemplateDraft, type LabTemplateDrafts } from "./LabTemplateResults";


interface LabActionsProps {
  test: LabTestDTO;
  onUpdated: (test: LabTestDTO) => void;
  onRefreshBlockedChange?: (blocked: boolean) => void;
  refreshing?: boolean;
  /** The test uses the review workflow, so the v1 result entry is hidden. */
  reviewWorkflow?: boolean;
  /** Called when the v1 result entry is refused because the test entered the review workflow. */
  onReviewRequired?: () => void;
}
const getServerRole = () => null;
const emptyResult = (): LabResultInput => ({ indicator: "", value: "", unit: null, referenceRange: null });

export function LabActions(props: LabActionsProps) {
  return <LabActionsContent key={props.test.testId} {...props} />;
}

function LabActionsContent({ test, onUpdated, onRefreshBlockedChange, refreshing = false, reviewWorkflow = false, onReviewRequired }: LabActionsProps) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const command = useCommand();
  const busy = command.busy;
  const [mode, setMode] = useState<"results" | "cancel" | null>(null);
  const [results, setResults] = useState<LabResultInput[]>([emptyResult()]);
  const [templateState, setTemplateState] = useState<"idle" | "loading" | "structured" | "legacy" | "error">("idle");
  const [template, setTemplate] = useState<LabResultTemplateDTO | null>(null);
  const [templateError, setTemplateError] = useState<string | null>(null);
  const [templateAttempt, setTemplateAttempt] = useState(0);
  const [templateDrafts, setTemplateDrafts] = useState<LabTemplateDrafts>({});
  const [conclusion, setConclusion] = useState("");
  const [performedDate, setPerformedDate] = useState(new Date().toISOString().slice(0, 10));
  const [cancelReason, setCancelReason] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (mode !== "results") return;
    let current = true;
    labApi.getTemplate(test.testId).then((response) => {
      if (response.testId !== test.testId) throw new Error("Mẫu kết quả không khớp xét nghiệm hiện tại.");
      if (!current) return;
      setTemplate(response.template);
      setTemplateState(response.template ? "structured" : "legacy");
    }).catch((cause: unknown) => {
      if (!current) return;
      if (cause instanceof ApiRequestError && cause.status === 404) {
        setTemplate(null);
        setTemplateState("legacy");
      } else {
        setTemplateError(cause instanceof Error ? cause.message : "Không tải được mẫu kết quả.");
        setTemplateState("error");
      }
    });
    return () => { current = false; };
  }, [mode, test.testId, templateAttempt]);

  const canWork = role === "ADMIN" || role === "LAB_TECH";
  const canCancel = role === "ADMIN" || role === "DOCTOR" || role === "LAB_TECH";
  const canStart = canWork && (test.careContractVersion === 1
    ? test.status === "READY" && Boolean(test.clearanceId)
    : test.status === "PENDING" || test.status === "READY");
  const canEnterResults = canWork && test.status === "IN_PROGRESS" && !reviewWorkflow;
  const canCancelCurrent = canCancel && !["COMPLETED", "CANCELLED"].includes(test.status);
  const refreshBlocked = busy || (mode !== null && !command.uncertain);
  useEffect(() => { onRefreshBlockedChange?.(refreshBlocked); }, [onRefreshBlockedChange, refreshBlocked]);
  if (!canStart && !canEnterResults && !canCancelCurrent) return null;

  async function start() {
    if (!canStart || busy || command.uncertain || refreshing) return;
    setValidationError(null);
    void command.run(() => labApi.start(test.testId), onUpdated);
  }

  async function submitResults(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canEnterResults || busy || command.uncertain || refreshing) return;
    if (templateState !== "legacy" && templateState !== "structured") return;
    let normalized: LabResultInput[];
    if (templateState === "structured" && template) {
      const mapped = mapTemplateResults(template.fields, templateDrafts);
      if (mapped.error) { setValidationError(mapped.error); return; }
      normalized = mapped.rows;
    } else {
      normalized = results.map((item) => ({
        indicator: item.indicator.trim(), value: item.value.trim(),
        unit: item.unit?.trim() || null, referenceRange: item.referenceRange?.trim() || null,
      }));
      if (normalized.some((item) => !item.indicator || !item.value)) {
        setValidationError("Mỗi chỉ số phải có tên và giá trị."); return;
      }
    }
    if (!performedDate || performedDate < test.requestedDate) {
      setValidationError("Ngày thực hiện phải từ ngày yêu cầu trở đi."); return;
    }
    setValidationError(null);
    void command.run(
      () => labApi.addResults(test.testId, { results: normalized, conclusion: conclusion.trim() || null, performedDate })
        .catch((cause: unknown) => {
          if (cause instanceof ApiRequestError && cause.code === "LAB_REVIEW_REQUIRED") return null;
          throw cause;
        }),
      (updated) => {
        setMode(null);
        if (updated) onUpdated(updated);
        else onReviewRequired?.();
      });
  }

  async function submitCancel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canCancelCurrent || busy || command.uncertain || refreshing) return;
    const reason = cancelReason.trim();
    if (!reason) { setValidationError("Lý do hủy là bắt buộc."); return; }
    setValidationError(null);
    void command.run(() => labApi.cancel(test.testId, { reason }), (updated) => { setMode(null); onUpdated(updated); });
  }

  function updateResult(index: number, field: keyof LabResultInput, value: string) {
    setResults((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, [field]: value } : item));
  }

  function updateTemplateDraft(fieldCode: string, change: Partial<LabTemplateDraft>) {
    setTemplateDrafts((current) => {
      const previous = current[fieldCode];
      return {
        ...current,
        [fieldCode]: {
          value: change.value ?? previous?.value ?? "",
          reason: change.reason ?? previous?.reason ?? "",
          operator: change.operator ?? previous?.operator ?? "",
        },
      };
    });
  }

  function openResults() {
    setValidationError(null);
    setTemplateState("loading");
    setTemplate(null);
    setTemplateError(null);
    setTemplateDrafts({});
    setMode("results");
  }

  function retryTemplate() {
    setTemplateState("loading");
    setTemplateError(null);
    setTemplate(null);
    setTemplateDrafts({});
    setTemplateAttempt((value) => value + 1);
  }

  return (
    <section className="border-b border-border py-5">
      <h2 className="text-lg font-semibold">Thao tác xét nghiệm</h2>
      <p className="mt-1 text-sm text-muted-foreground">Chỉ bắt đầu khi đã có xác nhận tài chính. Kiểm tra đúng bệnh nhân và chỉ số trước khi lưu kết quả.</p>
      <fieldset disabled={busy || command.uncertain || refreshing} className="min-w-0 space-y-4">
      <div className="mt-4 flex flex-wrap gap-2">
        {canStart ? <Button type="button" variant="primary" loading={busy} onClick={() => void start()}>Bắt đầu xét nghiệm</Button> : null}
        {canEnterResults && mode === null ? <Button type="button" variant="primary" disabled={busy} onClick={openResults}>Nhập kết quả</Button> : null}
        {canCancelCurrent && mode === null ? <details className="self-center"><summary className="min-h-11 cursor-pointer px-3 py-3 text-sm font-medium text-muted-foreground hover:text-foreground">Thao tác khác</summary><div className="py-2"><Button type="button" variant="ghost" disabled={busy} onClick={() => { setMode("cancel"); setValidationError(null); }}>Hủy xét nghiệm</Button></div></details> : null}
      </div>

      <CommandFeedback error={command.error} uncertain={command.uncertain} />
      {validationError ? <p role="alert" className="mt-3 text-sm text-danger">{validationError}</p> : null}

      {mode === "results" && templateState === "loading" ? <div className="mt-5 space-y-2 text-sm text-muted-foreground">
        <p role="status" aria-busy="true">Đang tải mẫu kết quả…</p>
        <Button type="button" variant="ghost" onClick={() => setMode(null)}>Quay lại</Button>
      </div> : null}
      {mode === "results" && templateState === "error" ? <div role="alert" className="mt-5 space-y-2 text-sm text-danger">
        <p>Không tải được mẫu kết quả: {templateError}</p>
        <Button type="button" variant="ghost" onClick={retryTemplate}>Thử tải mẫu lại</Button>
        <Button type="button" variant="ghost" onClick={() => setMode(null)}>Quay lại</Button>
      </div> : null}
      {mode === "results" && templateState === "structured" && template ? <form onSubmit={submitResults} className="mt-5 space-y-4 border-t border-border pt-5">
        <PatientContextBanner label="Đối chiếu bệnh nhân trước khi nhập kết quả" patientId={test.patientId} departmentId={test.requestingDepartmentId} date={test.requestedDate} dateLabel="Ngày yêu cầu">
          <p className="text-sm font-medium">{test.labType} · Đối chiếu đúng người và phiếu trước khi lưu</p>
        </PatientContextBanner>
        <h3 className="font-semibold">{template.name} · Kết quả chỉ số</h3>
        <LabTemplateResults fields={template.fields} drafts={templateDrafts} onDraftChange={updateTemplateDraft} />
        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Ngày thực hiện" htmlFor="lab-performed-date" required><input id="lab-performed-date" type="date" value={performedDate} onChange={(event) => setPerformedDate(event.target.value)} className={controlClassName} /></Field>
          <Field label="Kết luận" htmlFor="lab-conclusion"><textarea id="lab-conclusion" maxLength={4000} rows={3} value={conclusion} onChange={(event) => setConclusion(event.target.value)} className={controlClassName} /></Field>
        </div>
        <div className="flex gap-2"><Button type="submit" variant="primary" loading={busy}>Lưu kết quả</Button><Button type="button" disabled={busy} onClick={() => setMode(null)}>Quay lại</Button></div>
      </form> : null}

      {mode === "results" && templateState === "legacy" ? (
        <form onSubmit={submitResults} className="mt-5 space-y-4 border-t border-border pt-5">
          <PatientContextBanner label="Đối chiếu bệnh nhân trước khi nhập kết quả" patientId={test.patientId} departmentId={test.requestingDepartmentId} date={test.requestedDate} dateLabel="Ngày yêu cầu">
            <p className="text-sm font-medium">{test.labType} · Đối chiếu đúng người và phiếu trước khi lưu</p>
          </PatientContextBanner>
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
          <p className="text-sm text-muted-foreground md:col-span-2">Người hủy được ghi nhận theo tài khoản đang đăng nhập. Hủy xét nghiệm sẽ dừng xử lý chỉ định; thao tác này không tự hoàn tiền. Nếu đã thu tiền, thu ngân đối soát và xử lý khoản hoàn riêng.</p>
          {test.status === "IN_PROGRESS" ? <p className="text-sm text-warning md:col-span-2">Xét nghiệm đang thực hiện. Hãy trao đổi với bộ phận xét nghiệm trước khi xác nhận hủy.</p> : null}
          <Field label="Lý do hủy" htmlFor="lab-cancel-reason" required><textarea id="lab-cancel-reason" required maxLength={1000} rows={3} value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} className={controlClassName} /></Field>
          <div className="flex gap-2 md:col-span-2"><Button type="submit" variant="danger" loading={busy}>Xác nhận hủy</Button><Button type="button" disabled={busy} onClick={() => setMode(null)}>Quay lại</Button></div>
        </form>
      ) : null}
      </fieldset>
    </section>
  );
}
