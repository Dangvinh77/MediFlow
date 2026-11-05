"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { pharmacyApi } from "../../api";
import { prescriptionStatusPresentation } from "../../presentation";
import type { PrescriptionMode } from "../../prescriptionMode";
import type { PrescriptionDTO } from "../../types";
import { formatDate, formatVnd, isUuid } from "../../utils";
import { PharmacySessionBoundary, usePharmacyRequestScope } from "../PharmacySessionBoundary";
import { CreatePrescriptionForm } from "./CreatePrescriptionForm";

const PAGE_SIZE = 100;
// The search endpoint filters by patient only, so the record's prescriptions are
// collected page by page. The cap keeps one embedded panel from scanning forever.
const MAX_PAGES = 10;

interface RecordPrescriptionWorkspaceProps {
  recordId: string;
  patientId: string;
  canCreate: boolean;
  mode: PrescriptionMode | null;
}

interface WorkspaceState {
  key: string;
  status: "loading" | "ready" | "error" | "forbidden";
  prescriptions?: PrescriptionDTO[];
  message?: string;
}

function loadRecordPrescriptions(recordId: string, patientId: string, isCurrent: () => boolean) {
  return (async () => {
    const found: PrescriptionDTO[] = [];
    let truncated = false;
    for (let page = 0; ; page += 1) {
      const result = await pharmacyApi.searchPrescriptions({ patientId, page, size: PAGE_SIZE });
      if (!isCurrent()) return null;
      found.push(...result.content.filter(rx => rx.recordId === recordId));
      const lastPage = Math.max(0, result.totalPages - 1);
      if (page >= lastPage) break;
      if (page + 1 >= MAX_PAGES) { truncated = true; break; }
    }
    return { prescriptions: found, truncated };
  })();
}

export function RecordPrescriptionWorkspace(props: RecordPrescriptionWorkspaceProps) {
  return <PharmacySessionBoundary capability="canReadPrescription"><WorkspaceContent {...props} /></PharmacySessionBoundary>;
}

function WorkspaceContent({ recordId, patientId, canCreate, mode }: RecordPrescriptionWorkspaceProps) {
  const isCurrent = usePharmacyRequestScope("canReadPrescription");
  const [revision, setRevision] = useState(0);
  const [retry, setRetry] = useState(0);
  const [truncated, setTruncated] = useState(false);
  const key = JSON.stringify([recordId, patientId, revision, retry]);
  const [state, setState] = useState<WorkspaceState>({ key, status: "loading" });
  const identified = isUuid(recordId) && isUuid(patientId);
  const result: WorkspaceState = !identified ? { key, status: "ready", prescriptions: [] }
    : state.key === key ? state : { key, status: "loading" };

  useEffect(() => {
    if (!identified || !isCurrent()) return;
    let active = true;
    const current = () => active && isCurrent();
    loadRecordPrescriptions(recordId, patientId, current).then(loaded => {
      if (!loaded || !current()) return;
      setTruncated(loaded.truncated);
      setState({ key, status: "ready", prescriptions: loaded.prescriptions });
    }).catch((cause: unknown) => {
      if (!current()) return;
      const forbidden = cause instanceof ApiRequestError && [401, 403].includes(cause.status);
      setState({
        key,
        status: forbidden ? "forbidden" : "error",
        message: forbidden ? "Bạn không có quyền xem đơn thuốc của hồ sơ này. Hãy kiểm tra phiên đăng nhập."
          : "Không thể tải đơn thuốc của hồ sơ. Hãy thử lại.",
      });
    });
    return () => { active = false; };
  }, [key, recordId, patientId, identified, isCurrent]);

  const ready = result.status === "ready";
  const canPrescribe = canCreate && ready;
  const created = () => setRevision(value => value + 1);

  return <div className="mt-3 space-y-4">
    {result.status === "loading" && <p role="status" className="text-sm text-muted-foreground">Đang tải đơn thuốc…</p>}

    {result.message && <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p>{result.message}</p>
      {result.status === "error" && <Button type="button" variant="secondary" className="mt-3" onClick={() => setRetry(value => value + 1)}>Thử lại</Button>}
    </div>}

    {ready && <>
      {result.prescriptions?.length ? <ul className="space-y-2" aria-label="Đơn thuốc đã kê cho hồ sơ">
        {result.prescriptions.map(rx => <li key={rx.prescriptionId}>
          <Link href={`/pharmacy/prescriptions/${rx.prescriptionId}`} className="block rounded-lg border border-border p-3 hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-medium">{formatDate(rx.prescribedDate)}</span>
              <StatusBadge tone={prescriptionStatusPresentation[rx.status].tone}>{prescriptionStatusPresentation[rx.status].label}</StatusBadge>
              <span className="ml-auto tabular-nums text-muted-foreground">{formatVnd(rx.totalAmount)}</span>
            </div>
            <p className="mt-1 text-sm text-muted-foreground">{rx.lines.map(line => `${line.drugName || "Thuốc trong đơn"} × ${line.quantity}`).join(" · ")}</p>
          </Link>
        </li>)}
      </ul> : <p className="rounded-lg border border-border p-4 text-sm text-muted-foreground">Hồ sơ này chưa có đơn thuốc.</p>}
      {truncated && <p className="text-sm text-muted-foreground">Bệnh nhân có nhiều đơn thuốc; <Link className="font-medium text-primary underline" href={`/pharmacy/prescriptions?patientId=${encodeURIComponent(patientId)}`}>xem đầy đủ theo bệnh nhân</Link>.</p>}
    </>}

    {canPrescribe && mode && <details className="rounded-lg border border-border p-3">
      <summary className="min-h-10 cursor-pointer py-2 text-sm font-semibold text-foreground">Kê đơn cho lượt khám</summary>
      <div className="mt-3">
        {mode === "outpatient"
          ? <CreatePrescriptionForm mode={mode} sourceRecordId={recordId} onCreated={created} />
          : <p className="text-sm text-muted-foreground">
            Chế độ kê đơn hiện hành chọn bệnh nhân và hồ sơ trong form. <Link className="font-medium text-primary underline" href={`/pharmacy/prescriptions/new?patientId=${encodeURIComponent(patientId)}&recordId=${encodeURIComponent(recordId)}`}>Mở form kê đơn của hồ sơ này</Link>.
          </p>}
      </div>
    </details>}

    {canPrescribe && !mode && <p role="alert" className="text-sm text-danger">Chức năng kê đơn chưa sẵn sàng. Vui lòng liên hệ quản trị viên.</p>}
  </div>;
}
