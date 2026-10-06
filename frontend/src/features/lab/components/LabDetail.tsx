"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge, type StatusTone } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { labApi } from "../api";
import type { LabTestDTO, LabTestStatus } from "../types";

interface LabDetailProps {
  testId: string;
  notice?: "created";
}

interface RequestError {
  message: string;
  correlationId: string | null;
}

type DetailState =
  | { key: string; status: "idle" }
  | { key: string; status: "success"; test: LabTestDTO }
  | { key: string; status: "not-found" }
  | { key: string; status: "error"; error: RequestError };

const statusPresentation: Record<LabTestStatus, { label: string; tone: StatusTone }> = {
  PENDING: { label: "Chờ xử lý", tone: "warning" },
  AWAITING_PAYMENT: { label: "Chờ quyền tài chính", tone: "warning" },
  READY: { label: "Sẵn sàng", tone: "info" },
  IN_PROGRESS: { label: "Đang thực hiện", tone: "info" },
  COMPLETED: { label: "Đã hoàn tất", tone: "success" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
};

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải chi tiết xét nghiệm.",
    correlationId: null,
  };
}

function Identifier({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-all font-mono text-sm">{value ?? "—"}</dd>
    </div>
  );
}

export function LabDetail({ testId, notice }: LabDetailProps) {
  const router = useRouter();
  const [state, setState] = useState<DetailState>({ key: "", status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const requestKey = `${testId}\u0000${retryToken}`;
  const loading = state.key !== requestKey;
  const retry = useCallback(() => setRetryToken((value) => value + 1), []);

  useEffect(() => {
    let active = true;
    labApi.getById(testId)
      .then((test) => {
        if (active) setState({ key: requestKey, status: "success", test });
      })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }
        if (cause instanceof ApiRequestError && cause.status === 404) {
          setState({ key: requestKey, status: "not-found" });
          return;
        }
        setState({ key: requestKey, status: "error", error: getRequestError(cause) });
      });
    return () => {
      active = false;
    };
  }, [requestKey, router, testId]);

  if (loading) return <AsyncState kind="loading" message="Đang tải chi tiết xét nghiệm…" />;

  if (state.status === "not-found") {
    return (
      <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Không tìm thấy xét nghiệm</h2>
        <p className="mt-2 text-sm text-muted-foreground">Yêu cầu có thể đã bị xóa hoặc mã truy cập không còn hợp lệ.</p>
        <Link href="/lab" className="mt-4 inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Quay lại danh sách</Link>
      </section>
    );
  }

  if (state.status === "error") {
    return <AsyncState kind="error" message={state.error.message} correlationId={state.error.correlationId} onRetry={retry} />;
  }

  if (state.status !== "success") return <AsyncState kind="empty" message="Không có dữ liệu xét nghiệm." />;

  const test = state.test;
  const lifecycle = statusPresentation[test.status];
  const results = test.results ?? [];

  return (
    <section className="mt-6 space-y-6">
      {notice === "created" ? (
        <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">
          Đã tạo yêu cầu xét nghiệm thành công.
        </p>
      ) : null}
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{test.labType}</p>
          <p className="mt-1 break-all font-mono text-sm">{test.testId}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge tone={lifecycle.tone}>{lifecycle.label}</StatusBadge>
          <StatusBadge tone={test.paid ? "success" : "warning"}>{test.paid ? "Đã thanh toán" : "Chưa thanh toán"}</StatusBadge>
        </div>
      </div>

      <dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2 lg:grid-cols-3">
        <Identifier label="Bệnh nhân" value={test.patientId} />
        <Identifier label="Hồ sơ khám" value={test.recordId} />
        <Identifier label="Khoa yêu cầu" value={test.requestingDepartmentId} />
        <Identifier label="Nguồn chỉ định" value={test.sourceOrderId} />
        <Identifier label="Đợt chăm sóc" value={test.careEpisodeId} />
        <Identifier label="Xác nhận tài chính" value={test.clearanceId} />
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Loại đợt chăm sóc</dt><dd className="mt-1 text-sm">{test.careEpisodeType ?? "—"}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Mã giá</dt><dd className="mt-1 text-sm">{test.priceCode ?? "—"}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Phiên bản contract/kết quả</dt><dd className="mt-1 text-sm">{test.careContractVersion} / {test.resultVersion}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Ngày yêu cầu</dt><dd className="mt-1 text-sm">{formatLocalDate(test.requestedDate)}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Ngày thực hiện</dt><dd className="mt-1 text-sm">{test.performedDate ? formatLocalDate(test.performedDate) : "—"}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Ngày tạo</dt><dd className="mt-1 text-sm">{formatInstant(test.createdAt)}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Cập nhật</dt><dd className="mt-1 text-sm">{formatInstant(test.updatedAt)}</dd></div>
      </dl>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Kết quả xét nghiệm</h2>
        {results.length === 0 ? <p className="mt-3 text-sm text-muted-foreground">Chưa có chỉ số kết quả.</p> : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full min-w-2xl text-left text-sm">
              <thead className="border-b border-border"><tr><th scope="col" className="px-3 py-2">Chỉ số</th><th scope="col" className="px-3 py-2">Giá trị</th><th scope="col" className="px-3 py-2">Đơn vị</th><th scope="col" className="px-3 py-2">Khoảng tham chiếu</th></tr></thead>
              <tbody>{results.map((result) => <tr key={result.resultId} className="border-b border-border last:border-0"><td className="px-3 py-3 font-medium">{result.indicator}</td><td className="px-3 py-3">{result.value}</td><td className="px-3 py-3">{result.unit ?? "—"}</td><td className="px-3 py-3">{result.referenceRange ?? "—"}</td></tr>)}</tbody>
            </table>
          </div>
        )}
        <div className="mt-5 border-t border-border pt-4"><h3 className="text-sm font-semibold">Kết luận</h3><p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">{test.conclusion ?? "Chưa có kết luận."}</p></div>
      </section>

      {test.emergencyOverrideId ? <section className="rounded-xl border border-warning/40 bg-warning/10 p-4 text-sm"><p className="font-medium">Có phê duyệt ngoại lệ khẩn cấp</p><p className="mt-1 break-all font-mono text-xs">{test.emergencyOverrideId}</p></section> : null}
      <Link href="/lab" className="inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Quay lại danh sách</Link>
    </section>
  );
}
