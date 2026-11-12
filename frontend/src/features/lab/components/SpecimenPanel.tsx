"use client";

import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { useCommand } from "@/lib/use-command";
import type { LabSpecimenDTO } from "../types";
import { labReviewApi } from "../review-api";

type SpecimenState =
  | { key: string; status: "loading" }
  | { key: string; status: "disabled" | "forbidden" }
  | { key: string; status: "error"; message: string; correlationId: string | null }
  | { key: string; status: "success"; specimens: LabSpecimenDTO[] };

const getServerRole = () => null;

function decisionLabel(decision: LabSpecimenDTO["decision"]) {
  if (decision === "ACCEPTED") return "Đã tiếp nhận";
  if (decision === "REJECTED") return "Đã từ chối";
  return "Chờ quyết định";
}

export function SpecimenPanel({ testId }: { testId: string }) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  const [reconcileToken, setReconcileToken] = useState(0);
  return <SpecimenPanelContent key={`${testId}:${session}:${reconcileToken}`} testId={testId} onReconcile={() => setReconcileToken((value) => value + 1)} />;
}

function SpecimenPanelContent({ testId, onReconcile }: { testId: string; onReconcile: () => void }) {
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const [retryToken, setRetryToken] = useState(0);
  const requestKey = `${testId}:${retryToken}`;
  const [state, setState] = useState<SpecimenState>({ key: "", status: "loading" });
  const [specimenType, setSpecimenType] = useState("");
  const [additionalTestIds, setAdditionalTestIds] = useState("");
  const [replacement, setReplacement] = useState("");
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const [validationError, setValidationError] = useState<string | null>(null);
  const command = useCommand();
  const retry = useCallback(() => setRetryToken((value) => value + 1), []);

  useEffect(() => {
    let active = true;
    labReviewApi.specimens(testId).then((specimens) => {
      if (active) setState({ key: requestKey, status: "success", specimens });
    }).catch((cause: unknown) => {
      if (!active) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      if (cause instanceof ApiRequestError && cause.status === 404 && cause.code === "LAB_REVIEW_DISABLED") {
        setState({ key: requestKey, status: "disabled" });
        return;
      }
      if (cause instanceof ApiRequestError && cause.status === 403) { setState({ key: requestKey, status: "forbidden" }); return; }
      setState({
        key: requestKey,
        status: "error",
        message: cause instanceof Error ? cause.message : "Không thể tải thông tin mẫu bệnh phẩm.",
        correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
      });
    });
    return () => { active = false; };
  }, [requestKey, router, testId]);

  const current = state.key === requestKey ? state : { key: requestKey, status: "loading" as const };
  if (current.status === "disabled") return null;

  function reloadAfter<T>(work: () => Promise<T>) {
    setValidationError(null);
    void command.run(work, () => retry());
  }

  function collect(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const type = specimenType.trim();
    if (!type) { setValidationError("Loại bệnh phẩm là bắt buộc."); return; }
    const linkedIds = additionalTestIds.split(/[\n,]/).map((value) => value.trim()).filter(Boolean);
    if (type.length > 40) { setValidationError("Loại bệnh phẩm không được vượt quá 40 ký tự."); return; }
    if (linkedIds.length > 20) { setValidationError("Có thể liên kết tối đa 20 xét nghiệm bổ sung."); return; }
    setValidationError(null);
    void command.run(() => labReviewApi.collectSpecimen(testId, {
      specimenType: type,
      ...(linkedIds.length ? { additionalTestIds: linkedIds } : {}),
      ...(replacement ? { replacesSpecimenId: replacement } : {}),
    }), () => {
      setSpecimenType("");
      setAdditionalTestIds("");
      setReplacement("");
      retry();
    });
  }

  function decide(specimen: LabSpecimenDTO, decision: "ACCEPTED" | "REJECTED") {
    const reason = reasons[specimen.specimenId]?.trim();
    if (decision === "REJECTED" && !reason) {
      setValidationError("Cần nhập lý do khi từ chối bệnh phẩm.");
      return;
    }
    if (decision === "REJECTED" && reason && reason.length > 500) {
      setValidationError("Lý do từ chối không được vượt quá 500 ký tự.");
      return;
    }
    reloadAfter(() => labReviewApi.decideSpecimen(specimen.specimenId, {
      decision,
      ...(decision === "REJECTED" ? { reason } : {}),
    }));
  }

  return (
    <section aria-labelledby="lab-specimens-heading" className="space-y-4 border-t border-border pt-5">
      <div>
        <h2 id="lab-specimens-heading" className="text-lg font-semibold">Bệnh phẩm</h2>
        <p className="mt-1 text-sm text-muted-foreground">Theo dõi việc thu nhận, tiếp nhận và từ chối từng mẫu bệnh phẩm.</p>
      </div>
      {current.status === "loading" ? <AsyncState kind="loading" message="Đang tải bệnh phẩm…" /> : null}
      {current.status === "forbidden" ? <p role="status" className="text-sm text-muted-foreground">Bạn không có quyền xem bệnh phẩm của xét nghiệm này.</p> : null}
      {current.status === "error" ? <AsyncState kind="error" message={current.message} correlationId={current.correlationId} onRetry={retry} /> : null}
      {current.status === "success" && current.specimens.length === 0 ? <AsyncState kind="empty" message="Chưa có bệnh phẩm được thu nhận." /> : null}
      {current.status === "success" && current.specimens.length > 0 ? (
        <ul className="divide-y divide-border rounded-xl border border-border bg-surface">
          {current.specimens.map((specimen) => (
            <li key={specimen.specimenId} className="space-y-3 p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 className="font-medium">{specimen.specimenType}</h3>
                  <p className="mt-1 text-sm text-muted-foreground">Mã tiếp nhận: <span className="font-mono">{specimen.accession}</span></p>
                  <p className="text-sm text-muted-foreground">Bệnh phẩm: {decisionLabel(specimen.decision)}</p>
                  {specimen.rejectionReason ? <p className="text-sm text-danger">Lý do từ chối: {specimen.rejectionReason}</p> : null}
                  {specimen.replacesSpecimenId ? <p className="text-sm text-muted-foreground">Thay cho mẫu: <span className="font-mono">{specimen.replacesSpecimenId}</span></p> : null}
                </div>
                {role === "LAB_TECH" && specimen.decision === null ? (
                  <div className="flex flex-col gap-2 sm:min-w-64">
                    <label htmlFor={`specimen-reason-${specimen.specimenId}`} className="text-sm font-medium">Lý do từ chối</label>
                    <textarea
                      id={`specimen-reason-${specimen.specimenId}`}
                      value={reasons[specimen.specimenId] ?? ""}
                      onChange={(event) => setReasons((current) => ({ ...current, [specimen.specimenId]: event.target.value }))}
                      rows={2}
                      maxLength={500}
                      className="w-full rounded-lg border border-control-border bg-surface px-3 py-2 text-sm"
                    />
                    <div className="flex flex-wrap gap-2">
                      <Button type="button" variant="primary" disabled={command.busy || command.uncertain} onClick={() => decide(specimen, "ACCEPTED")}>Tiếp nhận</Button>
                      <Button type="button" variant="danger" disabled={command.busy || command.uncertain} onClick={() => decide(specimen, "REJECTED")}>Từ chối</Button>
                    </div>
                  </div>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      ) : null}

      {current.status === "success" && (role === "NURSE" || role === "LAB_TECH") ? (
        <form onSubmit={collect} className="grid gap-3 rounded-xl border border-border bg-surface p-4 md:grid-cols-2">
          <h3 className="font-semibold md:col-span-2">Thu nhận bệnh phẩm</h3>
          <label className="space-y-1 text-sm font-medium" htmlFor="lab-specimen-type">Loại bệnh phẩm
            <input id="lab-specimen-type" required value={specimenType} onChange={(event) => setSpecimenType(event.target.value)} maxLength={40} className="mt-1 min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 py-2 font-normal" />
          </label>
          <label className="space-y-1 text-sm font-medium" htmlFor="lab-specimen-additional-tests">Mã xét nghiệm bổ sung
            <textarea id="lab-specimen-additional-tests" value={additionalTestIds} onChange={(event) => setAdditionalTestIds(event.target.value)} rows={2} className="mt-1 w-full rounded-lg border border-control-border bg-surface px-3 py-2 font-normal" aria-describedby="lab-specimen-additional-help" />
            <span id="lab-specimen-additional-help" className="block font-normal text-muted-foreground">Có thể nhập nhiều mã, ngăn cách bằng dấu phẩy hoặc xuống dòng.</span>
          </label>
          <label className="space-y-1 text-sm font-medium md:col-span-2" htmlFor="lab-specimen-replacement">Thu mẫu lại
            <select id="lab-specimen-replacement" value={replacement} onChange={(event) => setReplacement(event.target.value)} className="mt-1 min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 py-2 font-normal">
              <option value="">Mẫu mới</option>
              {current.specimens.filter((item) => item.decision === "REJECTED").map((item) => <option key={item.specimenId} value={item.specimenId}>{item.accession} · {item.specimenType}</option>)}
            </select>
          </label>
          <div className="md:col-span-2"><Button type="submit" variant="primary" loading={command.busy} disabled={command.uncertain}>Thu nhận</Button></div>
        </form>
      ) : null}

      {validationError ? <p role="alert" className="text-sm text-danger">{validationError}</p> : null}
      {command.error ? <div role="alert" className="space-y-2 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
        <p>{command.error.code === "LAB_SPECIMEN_DECIDED" ? "Bệnh phẩm đã được quyết định bởi người khác. Hãy tải lại danh sách." : command.error.message}</p>
        {command.uncertain ? <Button type="button" onClick={onReconcile}>Đối soát và tải lại</Button> : command.error.code === "LAB_SPECIMEN_DECIDED" ? <Button type="button" onClick={retry}>Tải lại bệnh phẩm</Button> : null}
      </div> : null}
    </section>
  );
}
