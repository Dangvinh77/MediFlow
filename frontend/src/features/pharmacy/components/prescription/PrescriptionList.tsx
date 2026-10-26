"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName } from "@/components/ui/Field";
import { Pagination } from "@/components/ui/Pagination";
import { ApiRequestError } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import { pharmacyApi } from "../../api";
import type { PrescriptionDTO } from "../../types";
import { PharmacySessionBoundary, usePharmacyRequestScope } from "../PharmacySessionBoundary";
import { PharmacyIdentity } from "../PharmacyIdentity";
import { PrescriptionLookup } from "./PrescriptionLookup";
import { PrescriptionPatientPicker } from "./PrescriptionPatientPicker";
import { buildPrescriptionQuery, readPrescriptionQuery, prescriptionStatuses as statuses } from "../../prescriptionQuery";
import { formatVnd } from "../../utils";
import { StatusBadge } from "@/components/ui/StatusBadge";

export function PrescriptionList() {
  const raw = useSearchParams().toString();
  return <PharmacySessionBoundary capability="canReadPrescription"><ListContent key={raw} initialQuery={raw} /></PharmacySessionBoundary>;
}

function ListContent({ initialQuery }: { initialQuery: string }) {
  const isCurrent = usePharmacyRequestScope("canReadPrescription");
  const [query, setQuery] = useState(() => readPrescriptionQuery(initialQuery));
  const [pickerReset, setPickerReset] = useState(0);
  function updateQuery(next: typeof query) {
    if (!isCurrent()) return;
    const search = buildPrescriptionQuery(next);
    window.history.pushState(null, "", `/pharmacy/prescriptions${search ? `?${search}` : ""}`);
    setQuery(next);
  }
  const [retry, setRetry] = useState(0);
  const key = JSON.stringify([query, retry]);
  const [loaded, setLoaded] = useState<{ key: string; data?: PageResult<PrescriptionDTO>; error?: string; forbidden?: boolean } | null>(null);
  const result = loaded?.key === key ? loaded : null;
  useEffect(() => {
    if (!isCurrent()) return;
    let active = true;
    pharmacyApi.searchPrescriptions({ ...query, size: 20 }).then(data => {
      if (!active || !isCurrent()) return;
      const lastPage = Math.max(0, data.totalPages - 1);
      if (query.page > lastPage) {
        const corrected = { ...query, page: lastPage };
        const search = buildPrescriptionQuery(corrected);
        window.history.replaceState(null, "", `/pharmacy/prescriptions${search ? `?${search}` : ""}`);
        setQuery(corrected); return;
      }
      setLoaded({ key, data });
    }).catch((cause: unknown) => {
      if (!active || !isCurrent()) return;
      const forbidden = cause instanceof ApiRequestError && [401, 403].includes(cause.status);
      setLoaded({ key, forbidden, error: forbidden ? "Bạn không có quyền xem danh sách đơn thuốc. Hãy kiểm tra phiên đăng nhập."
        : "Không thể tải danh sách đơn thuốc. Hãy thử lại." });
    });
    return () => { active = false; };
  }, [query, key, isCurrent]);
  return <section className="mt-6 space-y-5" aria-label="Danh sách đơn thuốc">
    <div className="flex flex-wrap items-end gap-3">
      <div className="min-w-56 flex-1 space-y-2">
        <label htmlFor="prescription-patient" className="text-sm">Tìm theo tên bệnh nhân</label>
        <PrescriptionPatientPicker key={pickerReset} value={query.patientId}
          onChange={patientId => updateQuery({ ...query, patientId, page: 0 })} disabled={result?.forbidden} />
      </div>
      <label className="text-sm">Trạng thái đơn
        <select className={`${controlClassName} mt-2`} value={query.status} disabled={result?.forbidden} onChange={event => updateQuery({ ...query, status: event.target.value, page: 0 })}>
          <option value="">Tất cả trạng thái</option>
          {Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <Button type="button" variant="secondary" disabled={result?.forbidden} onClick={() => { updateQuery({ patientId: "", status: "", page: 0 }); setPickerReset(v => v + 1); }}>Xem tất cả</Button>
      <Button type="button" variant="secondary" disabled={result?.forbidden} onClick={() => setRetry(current => current + 1)}>Làm mới</Button>
    </div>
    <p className="text-sm text-muted-foreground">Tìm tên và chọn bệnh nhân để xem các đơn của người đó. Ngày sinh giúp phân biệt người trùng tên.</p>
    {!result && <p role="status">Đang tải đơn thuốc…</p>}
    {result?.error && <div role="alert" className="rounded-lg border border-danger/40 p-4 text-danger">{result.error}
      {!result.forbidden && <Button type="button" variant="secondary" onClick={() => setRetry(current => current + 1)}>Thử lại</Button>}
    </div>}
    {result?.data && <>
      <p className="text-sm text-muted-foreground">Có {result.data.totalElements} đơn thuốc{query.patientId || query.status ? " phù hợp bộ lọc" : " trong hệ thống"}.</p>
      {result.data.content.length === 0 ? <p className="rounded-lg border border-border p-6">{query.patientId || query.status ? "Không có đơn thuốc phù hợp. Thử đổi bệnh nhân hoặc trạng thái." : "Chưa có đơn thuốc. Đơn mới sẽ xuất hiện ở đây sau khi bác sĩ kê."}</p>
        : <div className="overflow-x-auto rounded-xl border border-border"><table className="w-full text-left text-sm">
          <caption className="sr-only">Các đơn thuốc phù hợp với bộ lọc đang chọn</caption>
          <thead className="bg-surface-muted"><tr>{["Ngày kê", "Bệnh nhân", "Thuốc đã kê", "Tổng tiền", "Trạng thái", "Thanh toán", "Thao tác"].map(label => <th scope="col" key={label} className="p-3 font-medium">{label}</th>)}</tr></thead>
          <tbody>{result.data.content.map(rx => <tr key={rx.prescriptionId} className="border-t border-border align-top">
            <td className="whitespace-nowrap p-3">{rx.prescribedDate.split("-").reverse().join("/")}</td>
            <td className="p-3"><PharmacyIdentity kind="patient" id={rx.patientId} /></td>
            <td className="p-3">{rx.lines.map(line => <div key={line.lineId}>{line.drugName || "Thuốc trong đơn"} × {line.quantity}</div>)}</td>
            <td className="whitespace-nowrap p-3 tabular-nums">{formatVnd(rx.totalAmount)}</td>
            <td className="p-3"><StatusBadge tone={rx.status === "FULFILLED" ? "success" : rx.status === "CANCELLED" || rx.status === "DISPENSE_FAILED" ? "danger" : "neutral"}>{statuses[rx.status] || rx.status}</StatusBadge>{rx.dispenseStatus === "DISPENSED" && rx.status !== "FULFILLED" ? " · Đã xuất thuốc" : ""}</td>
            <td className="p-3">{rx.paymentConfirmed ? "Đã xác nhận" : "Chưa xác nhận"}</td>
            <td className="p-3"><Link className="font-medium text-primary underline" href={`/pharmacy/prescriptions/${rx.prescriptionId}`}>Xem đơn</Link></td>
          </tr>)}</tbody>
        </table></div>}
      <Pagination page={query.page} totalPages={result.data.totalPages} totalElements={result.data.totalElements} loading={false} onPageChange={page => updateQuery({ ...query, page })} />
    </>}
    <details className="rounded-lg border border-border p-4"><summary className="cursor-pointer text-sm">Mở nhanh khi có mã đơn thuốc</summary><PrescriptionLookup variant="compact" /></details>
  </section>;
}
