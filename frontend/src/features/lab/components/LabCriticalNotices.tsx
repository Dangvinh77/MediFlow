"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ReferenceText } from "@/components/references/ReferenceText";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { useCommand } from "@/lib/use-command";
import type { LabCriticalNoticeDTO } from "../types";
import { labReviewApi } from "../review-api";

type NoticeState =
  | { key: number; status: "loading" }
  | { key: number; status: "disabled" }
  | { key: number; status: "error"; message: string; correlationId: string | null }
  | { key: number; status: "success"; notices: LabCriticalNoticeDTO[] };

const getServerRole = () => null;
const getServerSession = () => null;

function flagLabel(flag: LabCriticalNoticeDTO["flag"]) {
  const labels: Record<LabCriticalNoticeDTO["flag"], string> = {
    LOW: "Thấp", NORMAL: "Bình thường", HIGH: "Cao", CRITICAL_LOW: "Thấp nguy cấp", CRITICAL_HIGH: "Cao nguy cấp", UNDETERMINED: "Chưa xác định",
  };
  return labels[flag];
}

export function LabCriticalNotices() {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, getServerSession);
  const [reconcileToken, setReconcileToken] = useState(0);
  return <LabCriticalNoticesContent key={`${session}:${reconcileToken}`} onReconcile={() => setReconcileToken((value) => value + 1)} />;
}

function LabCriticalNoticesContent({ onReconcile }: { onReconcile: () => void }) {
  const { localize } = useLocale();
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const [retryToken, setRetryToken] = useState(0);
  const [state, setState] = useState<NoticeState>({ key: -1, status: "loading" });
  const command = useCommand();

  useEffect(() => {
    let active = true;
    labReviewApi.criticalNotices("OPEN").then((notices) => {
      if (active) setState({ key: retryToken, status: "success", notices });
    }).catch((cause: unknown) => {
      if (!active) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      if (cause instanceof ApiRequestError && cause.status === 404 && cause.code === "LAB_REVIEW_DISABLED") {
        setState({ key: retryToken, status: "disabled" });
        return;
      }
      setState({
        key: retryToken,
        status: "error",
        message: cause instanceof Error ? cause.message : "Không thể tải cảnh báo kết quả nguy cấp.",
        correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
      });
    });
    return () => { active = false; };
  }, [retryToken, router]);

  const current = state.key === retryToken ? state : { key: retryToken, status: "loading" as const };

  function acknowledge(notice: LabCriticalNoticeDTO) {
    void command.run(
      () => labReviewApi.acknowledgeCriticalNotice(notice.testId, notice.noticeId),
      () => setRetryToken((value) => value + 1),
    );
  }

  return (
    <section className="space-y-4">
      <L10nValue>{current.status === "loading" ? <AsyncState kind="loading" message={localize("Đang tải cảnh báo nguy cấp…")} /> : null}</L10nValue>
      <L10nValue>{current.status === "disabled" ? <AsyncState kind="empty" message={localize("Quy trình duyệt xét nghiệm hiện chưa được bật.")} /> : null}</L10nValue>
      <L10nValue>{current.status === "error" ? <AsyncState kind="error" message={localize(current.message)} correlationId={current.correlationId} onRetry={() => setRetryToken((value) => value + 1)} /> : null}</L10nValue>
      <L10nValue>{current.status === "success" && current.notices.length === 0 ? <AsyncState kind="empty" message={localize("Không có cảnh báo kết quả nguy cấp đang chờ xác nhận.")} /> : null}</L10nValue>
      <L10nValue>{current.status === "success" && current.notices.length > 0 ? <ul className="divide-y divide-border rounded-xl border border-border bg-surface">
        <L10nValue>{current.notices.map((notice) => <li key={notice.noticeId} className="flex flex-col gap-4 p-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0 space-y-1">
            <h2 className="font-semibold"><L10nValue>{notice.label}</L10nValue></h2>
            <p className="text-sm"><L10n>{"Kết quả: "}</L10n><strong>{notice.value}</strong><L10nValue>{notice.unit ? ` ${notice.unit}` : ""}</L10nValue> · <span><L10nValue>{flagLabel(notice.flag)}</L10nValue></span></p>
            <p className="text-sm"><L10n>{"Bệnh nhân: "}</L10n><ReferenceText kind="patient" value={notice.patientId} fallback="Bệnh nhân" /></p>
            <p className="text-sm text-muted-foreground"><L10n>{"Mã xét nghiệm: "}</L10n><span className="font-mono">{notice.testId}</span><L10n>{" · Phiên bản "}</L10n>{notice.resultVersion}</p>
            <p className="text-sm text-muted-foreground"><L10n>{"Thời điểm ghi nhận: "}</L10n><L10nValue>{formatInstant(notice.createdAt)}</L10nValue></p>
          </div>
          <L10nValue>{role === "DOCTOR" ? <Button type="button" variant="primary" loading={command.busy} disabled={command.uncertain} onClick={() => acknowledge(notice)}><L10n>{"Xác nhận đã nhận"}</L10n></Button> : null}</L10nValue>
        </li>)}</L10nValue>
      </ul> : null}</L10nValue>
      <L10nValue>{command.error ? <div role="alert" className="space-y-2 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
        <p><L10nValue>{command.error.code === "LAB_CRITICAL_NOT_RESPONSIBLE" ? "Bạn không phụ trách khoa của cảnh báo này nên chưa thể xác nhận." : command.error.message}</L10nValue></p>
        <L10nValue>{command.uncertain ? <Button type="button" onClick={onReconcile}><L10n>{"Đối soát và tải lại"}</L10n></Button> : command.error.code === "LAB_CRITICAL_NOT_RESPONSIBLE" ? <Button type="button" onClick={() => setRetryToken((value) => value + 1)}><L10n>{"Tải lại cảnh báo"}</L10n></Button> : null}</L10nValue>
      </div> : null}</L10nValue>
    </section>
  );
}
