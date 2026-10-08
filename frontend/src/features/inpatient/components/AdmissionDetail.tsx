"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { DataTableShell } from "@/components/ui/DataTableShell";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { inpatientApi } from "../api";
import {
  admissionPriorityLabel,
  admissionStatusPresentation,
  clinicalOrderTypeLabel,
  externalOrderStatusPresentation,
} from "../presentation";
import type { AdmissionDTO } from "../types";

interface RequestError { message: string; correlationId: string | null }
type DetailState =
  | { key: string; status: "idle" }
  | { key: string; status: "success"; admission: AdmissionDTO }
  | { key: string; status: "not-found" }
  | { key: string; status: "error"; error: RequestError };

function Identifier({ label, value }: { label: string; value: string | null }) {
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-all font-mono text-sm">{value ?? "—"}</dd></div>;
}

export function AdmissionDetail({ admissionId }: { admissionId: string }) {
  const router = useRouter();
  const [state, setState] = useState<DetailState>({ key: "", status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const requestKey = `${admissionId}\u0000${retryToken}`;
  const retry = useCallback(() => setRetryToken((value) => value + 1), []);

  useEffect(() => {
    let active = true;
    inpatientApi.getAdmission(admissionId).then((admission) => {
      if (active) setState({ key: requestKey, status: "success", admission });
    }).catch((cause: unknown) => {
      if (!active) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
      } else if (cause instanceof ApiRequestError && cause.status === 404) {
        setState({ key: requestKey, status: "not-found" });
      } else {
        setState({ key: requestKey, status: "error", error: {
          message: cause instanceof Error ? cause.message : "Không thể tải đợt nội trú.",
          correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
        } });
      }
    });
    return () => { active = false; };
  }, [admissionId, requestKey, router]);

  if (state.key !== requestKey) return <AsyncState kind="loading" message="Đang tải đợt nội trú…" />;
  if (state.status === "not-found") return <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6"><h2 className="text-lg font-semibold">Không tìm thấy đợt nội trú</h2><p className="mt-2 text-sm text-muted-foreground">Mã hồ sơ không tồn tại hoặc không còn được phép truy cập.</p><Link href="/inpatient" className="mt-4 inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold hover:bg-surface-muted">Quay lại danh sách</Link></section>;
  if (state.status === "error") return <AsyncState kind="error" message={state.error.message} correlationId={state.error.correlationId} onRetry={retry} />;
  if (state.status !== "success") return <AsyncState kind="empty" message="Không có dữ liệu nội trú." />;

  const admission = state.admission;
  const lifecycle = admissionStatusPresentation[admission.status];
  return (
    <section className="mt-6 space-y-6">
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 sm:flex-row sm:items-start sm:justify-between">
        <div><p className="text-sm text-muted-foreground">Mã đợt nội trú</p><p className="mt-1 break-all font-mono text-sm">{admission.maDotNoiTru}</p></div>
        <div className="flex flex-wrap gap-2"><StatusBadge tone={lifecycle.tone}>{lifecycle.label}</StatusBadge><StatusBadge tone={admission.capCuu ? "danger" : "neutral"}>{admissionPriorityLabel[admission.doUuTien]}</StatusBadge></div>
      </div>

      <dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2 xl:grid-cols-3">
        <Identifier label="Bệnh nhân" value={admission.maBenhNhan} />
        <Identifier label="Hồ sơ nguồn" value={admission.maHoSoNguon} />
        <Identifier label="Yêu cầu nội trú" value={admission.maYeuCauNoiTru} />
        <Identifier label="Khoa" value={admission.maKhoa} />
        <Identifier label="Giường hiện tại" value={admission.maGiuongDangSuDung} />
        <Identifier label="Xác nhận tạm ứng" value={admission.maXacNhanTamUng} />
        <Identifier label="Quyết toán" value={admission.maQuyetToan} />
        <Identifier label="Tóm tắt ra viện" value={admission.maTomTatRaVien} />
        <Identifier label="Phê duyệt ngoại lệ" value={admission.maPheDuyetNgoaiLe} />
      </dl>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h2 className="text-base font-semibold">Mốc thời gian</h2>
        <dl className="mt-4 grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          <div><dt className="text-xs uppercase tracking-wide text-muted-foreground">Yêu cầu</dt><dd className="mt-1 text-sm">{formatInstant(admission.thoiGianYeuCau)}</dd></div>
          <div><dt className="text-xs uppercase tracking-wide text-muted-foreground">Nhập viện</dt><dd className="mt-1 text-sm">{formatInstant(admission.thoiGianNhapVien)}</dd></div>
          <div><dt className="text-xs uppercase tracking-wide text-muted-foreground">Ra viện y tế</dt><dd className="mt-1 text-sm">{formatInstant(admission.thoiGianRaVienYTe)}</dd></div>
          <div><dt className="text-xs uppercase tracking-wide text-muted-foreground">Đóng hồ sơ</dt><dd className="mt-1 text-sm">{formatInstant(admission.thoiGianDong)}</dd></div>
          <div><dt className="text-xs uppercase tracking-wide text-muted-foreground">Hủy</dt><dd className="mt-1 text-sm">{formatInstant(admission.thoiGianHuy)}</dd></div>
          <div><dt className="text-xs uppercase tracking-wide text-muted-foreground">Lý do hủy</dt><dd className="mt-1 whitespace-pre-wrap text-sm">{admission.lyDoHuy ?? "—"}</dd></div>
        </dl>
      </section>

      <DataTableShell title="Y lệnh liên service" description="Tham chiếu chỉ đọc do Inpatient lưu; hệ thống nguồn vẫn là thẩm quyền.">
        {admission.yeuLenhNgoai.length === 0 ? <p className="px-5 py-6 text-sm text-muted-foreground">Chưa có y lệnh liên service.</p> : (
          <table className="w-full min-w-3xl text-left text-sm"><thead className="bg-surface-muted"><tr><th className="px-4 py-3">Loại</th><th className="px-4 py-3">Y lệnh nguồn</th><th className="px-4 py-3">Trạng thái</th><th className="px-4 py-3">Tóm tắt</th><th className="px-4 py-3">Phiên bản</th></tr></thead><tbody>{admission.yeuLenhNgoai.map((order) => { const presentation = externalOrderStatusPresentation[order.status]; return <tr key={order.maThamChieu} className="border-t border-border align-top"><td className="px-4 py-3 font-medium">{clinicalOrderTypeLabel[order.loaiYLenh]}</td><td className="break-all px-4 py-3 font-mono text-xs">{order.maYLenhBenNgoai}</td><td className="px-4 py-3"><StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge></td><td className="max-w-md whitespace-normal px-4 py-3">{order.tomTat ?? "—"}</td><td className="px-4 py-3">{order.phienBanSuKien ?? "—"}</td></tr>; })}</tbody></table>
        )}
      </DataTableShell>

      <Link href="/inpatient" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold hover:bg-surface-muted">Quay lại danh sách</Link>
    </section>
  );
}
