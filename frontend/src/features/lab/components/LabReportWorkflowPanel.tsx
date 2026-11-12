"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { useCapabilities } from "@/lib/capabilities";
import { labApi } from "../api";
import type { LabReportDTO, LabResultFlag, LabResultTemplateDTO, LabTestDTO } from "../types";
import { LabTemplateResults, type LabTemplateDrafts } from "./LabTemplateResults";

type PanelTest = Pick<LabTestDTO, "testId" | "status" | "requestedDate">;

interface LabReportWorkflowPanelProps {
  test: PanelTest;
  /** A report already read by the caller; `null` means "no report yet". Omit to let the panel read it. */
  initialReport?: LabReportDTO | null;
  onChanged?: () => void;
}

type Mode = "return" | "amend" | null;

const flagLabels: Record<LabResultFlag, string> = {
  LOW: "Thấp",
  NORMAL: "Bình thường",
  HIGH: "Cao",
  CRITICAL_LOW: "Thấp nguy cấp",
  CRITICAL_HIGH: "Cao nguy cấp",
  UNDETERMINED: "Chưa xác định",
};

const statusLabels: Record<LabReportDTO["status"], string> = {
  DRAFT: "Bản nháp",
  PENDING_REVIEW: "Chờ duyệt",
  RELEASED: "Đã phát hành",
};

const errorMessages: Record<string, string> = {
  LAB_REPORT_STALE: "Nhân viên khác đã lưu báo cáo này. Tải lại để xem bản mới nhất trước khi sửa tiếp.",
  LAB_REPORT_NOT_EDITABLE: "Báo cáo đang chờ duyệt hoặc đã phát hành nên không sửa được.",
  LAB_SELF_RELEASE: "Không thể tự phát hành báo cáo do chính bạn nhập hoặc gửi duyệt.",
  LAB_RELEASE_DENIED: "Tài khoản chưa được cấp quyền phát hành kết quả xét nghiệm.",
  LAB_SPECIMEN_REQUIRED: "Chưa có mẫu bệnh phẩm được chấp nhận cho xét nghiệm này.",
  LAB_REPORT_TEST_NOT_IN_PROGRESS: "Xét nghiệm chưa ở trạng thái đang thực hiện.",
  LAB_REPORT_TEMPLATE_REQUIRED: "Xét nghiệm chưa gắn template, hãy dùng cách nhập kết quả cũ.",
  LAB_AMENDMENT_REASON_REQUIRED: "Vui lòng nhập lý do đính chính.",
  LAB_AMENDMENT_NOT_RELEASED: "Chỉ đính chính được báo cáo đã phát hành.",
  LAB_AMENDMENT_DISABLED: "Chức năng đính chính chưa được bật.",
};

function draftsFrom(report: LabReportDTO | null): LabTemplateDrafts {
  const drafts: LabTemplateDrafts = {};
  for (const value of report?.values ?? []) {
    drafts[value.fieldCode] = { value: value.value ?? "", operator: value.operator ?? "", reason: value.noResultReason ?? "" };
  }
  return drafts;
}

function isCode(cause: unknown, code: string) {
  return cause instanceof ApiRequestError && cause.code === code;
}

export function LabReportWorkflowPanel({ test, initialReport, onChanged }: LabReportWorkflowPanelProps) {
  const { localize } = useLocale();
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const staffId = useCapabilities().data?.staffId ?? null;
  const [loaded, setLoaded] = useState(initialReport !== undefined);
  const [report, setReport] = useState<LabReportDTO | null>(initialReport ?? null);
  const [template, setTemplate] = useState<LabResultTemplateDTO | null>(null);
  const [drafts, setDrafts] = useState<LabTemplateDrafts>(() => draftsFrom(initialReport ?? null));
  const [conclusion, setConclusion] = useState(initialReport?.conclusion ?? "");
  const [mode, setMode] = useState<Mode>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ message: string; stale: boolean } | null>(null);

  const apply = useCallback((next: LabReportDTO | null) => {
    setReport(next);
    setDrafts(draftsFrom(next));
    setConclusion(next?.conclusion ?? "");
  }, []);

  const readReport = useCallback(async () => {
    try {
      return await labApi.getReport(test.testId);
    } catch (cause) {
      if (isCode(cause, "LAB_REPORT_NOT_FOUND")) return null;
      throw cause;
    }
  }, [test.testId]);

  useEffect(() => {
    let active = true;
    labApi.getTemplate(test.testId)
      .then((result) => { if (active) setTemplate(result.template); })
      .catch(() => { if (active) setTemplate(null); });
    if (initialReport === undefined) {
      readReport()
        .then((next) => { if (active) { apply(next); setLoaded(true); } })
        .catch((cause: unknown) => { if (active) { setLoaded(true); fail(cause); } });
    }
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- the initial read runs once per test
  }, [test.testId]);

  function fail(cause: unknown) {
    if (cause instanceof ApiRequestError && cause.status === 401) {
      router.replace("/login");
      return;
    }
    const code = cause instanceof ApiRequestError ? cause.code : null;
    const message = (code && errorMessages[code])
      ?? (cause instanceof Error ? cause.message : "Không thể xử lý báo cáo xét nghiệm.");
    setError({ message, stale: code === "LAB_REPORT_STALE" });
  }

  async function run(action: () => Promise<LabReportDTO>) {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      apply(await action());
      setMode(null);
      setReason("");
      onChanged?.();
    } catch (cause) {
      fail(cause);
    } finally {
      setBusy(false);
    }
  }

  async function reload() {
    setError(null);
    try {
      apply(await readReport());
    } catch (cause) {
      fail(cause);
    }
  }

  function saveDraft() {
    const values = Object.entries(drafts)
      .map(([fieldCode, draft]) => ({
        fieldCode,
        operator: draft.operator.trim() || undefined,
        value: draft.value.trim() || undefined,
        noResultReason: draft.reason.trim() || undefined,
      }))
      .filter((value) => value.value !== undefined || value.noResultReason !== undefined);
    void run(() => labApi.saveReportDraft(test.testId, {
      draftRevision: report?.draftRevision ?? 0,
      values,
      conclusion: conclusion.trim() || undefined,
      performedDate: report?.performedDate ?? new Date().toISOString().slice(0, 10),
    }));
  }

  function confirmReason() {
    const trimmed = reason.trim();
    if (!trimmed) {
      setError({ message: mode === "return" ? "Vui lòng nhập lý do trả lại." : "Vui lòng nhập lý do đính chính.", stale: false });
      return;
    }
    void run(() => mode === "return"
      ? labApi.returnReport(test.testId, { reason: trimmed })
      : labApi.amendReport(test.testId, { reason: trimmed }));
  }

  const isLabTech = role === "LAB_TECH";
  const status = report?.status ?? "DRAFT";
  const editable = isLabTech && status === "DRAFT" && template !== null;
  const ownWork = staffId !== null && (report?.enteredBy === staffId || report?.submittedBy === staffId);
  const labelOf = (fieldCode: string) => template?.fields.find((field) => field.fieldCode === fieldCode)?.label ?? fieldCode;

  return (
    <section className="space-y-4 border-b border-border py-5" aria-busy={busy}>
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-semibold"><L10n>{"Báo cáo xét nghiệm"}</L10n></h2>
        <L10nValue>{report ? <span className="text-sm text-muted-foreground">
          <L10nValue>{statusLabels[report.status]}</L10nValue><L10n>{" · bản "}</L10n>{report.resultVersion}
        </span> : loaded ? <span className="text-sm text-muted-foreground"><L10n>{"Chưa có bản nháp"}</L10n></span> : null}</L10nValue>
      </div>

      <L10nValue>{!loaded ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Đang tải báo cáo…"}</L10n></p> : null}</L10nValue>

      <L10nValue>{report?.amendmentReason ? <p className="text-sm"><L10n>{"Lý do đính chính: "}</L10n>{report.amendmentReason}</p> : null}</L10nValue>
      <L10nValue>{report?.returnReason && status === "DRAFT" ? <p className="text-sm text-warning"><L10n>{"Bị trả lại: "}</L10n>{report.returnReason}</p> : null}</L10nValue>

      <L10nValue>{report && report.values.length > 0 && !editable ? (
        <ul className="divide-y divide-border rounded-lg border border-border text-sm">
          <L10nValue>{report.values.map((value) => (
            <li key={value.fieldCode} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
              <span className="font-medium"><L10nValue>{labelOf(value.fieldCode)}</L10nValue></span>
              <span><L10nValue>{value.noResultReason ? `Không có kết quả: ${value.noResultReason}` : `${value.operator ?? ""}${value.value ?? ""}`}</L10nValue></span>
              <L10nValue>{value.flag ? <span className={value.flag.startsWith("CRITICAL") ? "font-semibold text-danger" : value.flag === "UNDETERMINED" ? "text-warning" : "text-muted-foreground"}><L10nValue>{flagLabels[value.flag]}</L10nValue></span> : null}</L10nValue>
            </li>
          ))}</L10nValue>
        </ul>
      ) : null}</L10nValue>
      <L10nValue>{report && editable ? (
        <ul className="flex flex-wrap gap-2 text-xs">
          <L10nValue>{report.values.filter((value) => value.flag).map((value) => (
            <li key={value.fieldCode} className="rounded border border-border px-2 py-1"><L10nValue>{labelOf(value.fieldCode)}</L10nValue>: <span><L10nValue>{flagLabels[value.flag!]}</L10nValue></span></li>
          ))}</L10nValue>
        </ul>
      ) : null}</L10nValue>

      <L10nValue>{editable && template ? (
        <div className="space-y-4">
          <LabTemplateResults fields={template.fields} drafts={drafts}
            onDraftChange={(fieldCode, change) => setDrafts((current) => ({
              ...current,
              [fieldCode]: { ...(current[fieldCode] ?? { value: "", reason: "", operator: "" }), ...change },
            }))} />
          <Field label={localize("Kết luận")} htmlFor="lab-report-conclusion">
            <textarea id="lab-report-conclusion" rows={2} value={conclusion} onChange={(event) => setConclusion(event.target.value)} className={controlClassName} />
          </Field>
        </div>
      ) : null}</L10nValue>

      <L10nValue>{error ? (
        <div role="alert" className="rounded-lg border border-danger/30 bg-danger/5 p-3 text-sm text-danger">
          <p><L10nValue>{error.message}</L10nValue></p>
          <L10nValue>{error.stale ? <Button type="button" variant="ghost" className="mt-2" onClick={() => void reload()}><L10n>{"Tải lại bản mới nhất"}</L10n></Button> : null}</L10nValue>
        </div>
      ) : null}</L10nValue>

      <L10nValue>{mode ? (
        <Field label={localize(mode === "return" ? "Lý do trả lại" : "Lý do đính chính")} htmlFor="lab-report-reason">
          <textarea id="lab-report-reason" rows={2} value={reason} onChange={(event) => setReason(event.target.value)} className={controlClassName} />
        </Field>
      ) : null}</L10nValue>

      <L10nValue>{isLabTech ? (
        <div className="flex flex-wrap gap-3">
          <L10nValue>{editable ? <>
            <Button type="button" disabled={busy} onClick={saveDraft}><L10n>{"Lưu nháp"}</L10n></Button>
            <L10nValue>{report ? <Button type="button" variant="primary" disabled={busy}
              onClick={() => void run(() => labApi.submitReport(test.testId, { draftRevision: report.draftRevision }))}><L10n>{"Gửi duyệt"}</L10n></Button> : null}</L10nValue>
          </> : null}</L10nValue>
          <L10nValue>{status === "PENDING_REVIEW" && mode === null ? <>
            <Button type="button" disabled={busy} onClick={() => { setError(null); setMode("return"); }}><L10n>{"Trả lại"}</L10n></Button>
            <L10nValue>{!ownWork ? <Button type="button" variant="primary" disabled={busy}
              onClick={() => void run(() => labApi.releaseReport(test.testId))}><L10n>{"Phát hành"}</L10n></Button> : null}</L10nValue>
          </> : null}</L10nValue>
          <L10nValue>{status === "RELEASED" && mode === null ? (
            <Button type="button" disabled={busy} onClick={() => { setError(null); setMode("amend"); }}><L10n>{"Đính chính"}</L10n></Button>
          ) : null}</L10nValue>
          <L10nValue>{mode ? <>
            <Button type="button" variant="primary" disabled={busy} onClick={confirmReason}>
              <L10nValue>{mode === "return" ? "Xác nhận trả lại" : "Tạo bản đính chính"}</L10nValue>
            </Button>
            <Button type="button" variant="ghost" disabled={busy} onClick={() => { setMode(null); setReason(""); setError(null); }}><L10n>{"Hủy"}</L10n></Button>
          </> : null}</L10nValue>
        </div>
      ) : null}</L10nValue>
      <L10nValue>{isLabTech && status === "PENDING_REVIEW" && ownWork ? (
        <p className="text-xs text-muted-foreground"><L10n>{"Người nhập hoặc gửi duyệt không được tự phát hành; cần người thứ hai."}</L10n></p>
      ) : null}</L10nValue>
    </section>
  );
}
