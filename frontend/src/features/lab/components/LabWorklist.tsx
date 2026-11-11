"use client";

import Link from "next/link";
import { useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import { formatInstant } from "@/lib/format";
import type { LabWorklistItemDTO } from "../types";
import { labReviewApi } from "../review-api";

type WorklistState =
  | { key: number; status: "loading" }
  | { key: number; status: "disabled" }
  | { key: number; status: "error"; message: string; correlationId: string | null }
  | { key: number; status: "success"; items: LabWorklistItemDTO[] };

const getServerSession = () => null;

const testStatusLabels: Record<string, string> = {
  PENDING: "Chờ xử lý", AWAITING_PAYMENT: "Chờ xác nhận thanh toán", READY: "Sẵn sàng",
  IN_PROGRESS: "Đang thực hiện", COMPLETED: "Đã hoàn tất", CANCELLED: "Đã hủy",
};

function specimenLabel(status: LabWorklistItemDTO["specimenStatus"]) {
  const labels: Record<LabWorklistItemDTO["specimenStatus"], string> = {
    NOT_COLLECTED: "Chưa thu mẫu", COLLECTED: "Đã thu mẫu", ACCEPTED: "Đã tiếp nhận", REJECTED: "Bị từ chối",
  };
  return labels[status];
}

function reportLabel(status: LabWorklistItemDTO["reportStatus"]) {
  if (!status) return "Chưa có phiếu";
  return { DRAFT: "Bản nháp", PENDING_REVIEW: "Chờ duyệt", RELEASED: "Đã phát hành" }[status];
}

export function LabWorklist() {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, getServerSession);
  return <LabWorklistContent key={session} />;
}

function LabWorklistContent() {
  const router = useRouter();
  const [retryToken, setRetryToken] = useState(0);
  const [state, setState] = useState<WorklistState>({ key: -1, status: "loading" });
  const [group, setGroup] = useState("");

  useEffect(() => {
    let active = true;
    labReviewApi.worklist().then((items) => {
      if (active) setState({ key: retryToken, status: "success", items });
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
        message: cause instanceof Error ? cause.message : "Không thể tải hàng đợi xét nghiệm.",
        correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
      });
    });
    return () => { active = false; };
  }, [retryToken, router]);

  const current = state.key === retryToken ? state : { key: retryToken, status: "loading" as const };
  const groups = current.status === "success"
    ? [...new Set(current.items.map((item) => item.displayGroup))].sort()
    : [];
  const filtered = current.status === "success"
    ? current.items.filter((item) => !group || item.displayGroup === group)
    : [];

  return (
    <section className="space-y-4">
      {current.status === "loading" ? <AsyncState kind="loading" message="Đang tải hàng đợi xét nghiệm…" /> : null}
      {current.status === "disabled" ? <AsyncState kind="empty" message="Quy trình duyệt xét nghiệm hiện chưa được bật." /> : null}
      {current.status === "error" ? <AsyncState kind="error" message={current.message} correlationId={current.correlationId} onRetry={() => setRetryToken((value) => value + 1)} /> : null}
      {current.status === "success" ? <>
        <div className="flex flex-wrap items-end gap-3 rounded-xl border border-border bg-surface p-4">
          <label htmlFor="lab-worklist-group" className="min-w-56 space-y-1 text-sm font-medium">Nhóm xét nghiệm
            <select id="lab-worklist-group" value={group} onChange={(event) => setGroup(event.target.value)} className="mt-1 min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 py-2">
              <option value="">Tất cả nhóm</option>
              {groups.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <p className="text-sm text-muted-foreground">{filtered.length} xét nghiệm</p>
        </div>
        {filtered.length === 0 ? <AsyncState kind="empty" message={group ? "Không có xét nghiệm trong nhóm đã chọn." : "Chưa có xét nghiệm trong hàng đợi."} /> : (
          <div className="overflow-x-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-5xl text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">Xét nghiệm</th>
                  <th scope="col" className="px-4 py-3">Nhóm</th>
                  <th scope="col" className="px-4 py-3">Trạng thái xét nghiệm</th>
                  <th scope="col" className="px-4 py-3">Bệnh phẩm</th>
                  <th scope="col" className="px-4 py-3">Phiếu kết quả</th>
                  <th scope="col" className="px-4 py-3">Hạn xử lý</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((item) => <tr key={item.testId} className={`border-b border-border align-top last:border-0 ${item.overdue ? "bg-danger/10" : ""}`}>
                  <td className="px-4 py-3">
                    <Link href={`/lab/${encodeURIComponent(item.testId)}`} className="font-medium text-primary underline-offset-4 hover:underline">{item.serviceName || item.labType}</Link>
                    <p className="mt-1 text-xs text-muted-foreground">{item.serviceCode}</p>
                    {item.overdue ? <span className="mt-2 inline-flex rounded-full bg-danger/15 px-2 py-1 text-xs font-semibold text-danger">Quá hạn</span> : null}
                  </td>
                  <td className="px-4 py-3">{item.displayGroup}</td>
                  <td className="px-4 py-3">{testStatusLabels[item.testStatus] ?? "Không xác định"}</td>
                  <td className="px-4 py-3">{specimenLabel(item.specimenStatus)}</td>
                  <td className="px-4 py-3">{reportLabel(item.reportStatus)}{item.reportVersion !== null ? ` · Phiên bản ${item.reportVersion}` : ""}</td>
                  <td className={`px-4 py-3 ${item.overdue ? "font-semibold text-danger" : "text-muted-foreground"}`}>{item.dueAt ? formatInstant(item.dueAt) : "Chưa có hạn"}</td>
                </tr>)}
              </tbody>
            </table>
          </div>
        )}
      </> : null}
    </section>
  );
}
