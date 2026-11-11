"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { medicalRecordApi } from "../api";
import type { RecordDocumentDTO } from "../types";

type PanelState =
  | { key: string; status: "loading" }
  | { key: string; status: "error"; message: string; correlationId: string | null }
  | { key: string; status: "forbidden" }
  | { key: string; status: "empty" }
  | { key: string; status: "success"; documents: RecordDocumentDTO[] };

function formatClinicalInstant(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const part = (name: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === name)?.value ?? "";
  return `${part("hour")}:${part("minute")} ${part("day")}/${part("month")}/${part("year")}`;
}

const documentTypeLabels = { LAB: "Xét nghiệm", PRESCRIPTION: "Đơn thuốc" } as const;

export function RecordDocumentsPanel({
  recordId,
  sourceHref,
}: {
  recordId: string;
  sourceHref?: (document: RecordDocumentDTO) => string | undefined;
}) {
  const router = useRouter();
  const [retryToken, setRetryToken] = useState(0);
  const key = `${recordId}:${retryToken}`;
  const [state, setState] = useState<PanelState>({ key, status: "loading" });

  useEffect(() => {
    let active = true;
    medicalRecordApi.documents(recordId)
      .then((documents) => {
        if (!active) return;
        setState(documents.length ? { key, status: "success", documents } : { key, status: "empty" });
      })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
        if (cause instanceof ApiRequestError && cause.status === 403) { setState({ key, status: "forbidden" }); return; }
        setState({
          key,
          status: "error",
          message: "Không thể tải danh sách tài liệu của lượt khám.",
          correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
        });
      });
    return () => { active = false; };
  }, [key, recordId, router]);

  const current = state.key === key ? state : { key, status: "loading" as const };
  return (
    <section aria-labelledby="record-documents-heading" className="space-y-3 border-t border-border pt-4">
      <h2 id="record-documents-heading" className="text-lg font-semibold">Tài liệu của lượt khám</h2>
      {current.status === "loading" ? <AsyncState kind="loading" message="Đang tải tài liệu của lượt khám…" /> : null}
      {current.status === "error" ? <AsyncState kind="error" message={current.message} correlationId={current.correlationId} onRetry={() => setRetryToken((value) => value + 1)} /> : null}
      {current.status === "forbidden" ? <p role="status" className="text-sm text-muted-foreground">Bạn không có quyền xem tài liệu của hồ sơ này.</p> : null}
      {current.status === "empty" ? <AsyncState kind="empty" message="Chưa có tài liệu cho lượt khám này." /> : null}
      {current.status === "success" ? (
        <ul className="divide-y divide-border rounded-xl border border-border bg-surface">
          {current.documents.map((document) => {
            const href = sourceHref?.(document);
            return (
              <li key={`${document.type}:${document.referenceId}`} className="flex flex-col gap-1 p-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <p className="text-xs font-medium text-muted-foreground">{documentTypeLabels[document.type]}</p>
                  {href ? <Link href={href} className="font-medium text-primary underline-offset-4 hover:underline">{document.summary || documentTypeLabels[document.type]}</Link>
                    : <p className="font-medium">{document.summary || documentTypeLabels[document.type]}</p>}
                  {document.afterConclusion ? <p className="mt-1 text-sm text-warning">Bổ sung sau khi kết thúc</p> : null}
                </div>
                <time dateTime={document.attachedAt} className="shrink-0 text-sm text-muted-foreground">{formatClinicalInstant(document.attachedAt)}</time>
              </li>
            );
          })}
        </ul>
      ) : null}
    </section>
  );
}
