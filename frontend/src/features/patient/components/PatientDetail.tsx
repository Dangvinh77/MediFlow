"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/auth";
import { getToken, subscribeToAuthChanges } from "@/lib/session";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { patientApi } from "../api";
import { patientPermissions } from "../logic";
import type { PatientDTO } from "../types";
import { usePatientMutation } from "../usePatientMutation";

interface PatientDetailProps { patientId: string; notice?: "created" | "updated" }
type State = { key: string; status: "idle" } | { key: string; status: "success"; patient: PatientDTO } | { key: string; status: "not-found" } | { key: string; status: "error"; statusCode?: number; message: string; correlationId: string | null };
const getServerSession = () => null;

export function PatientDetail(props: PatientDetailProps) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getToken, getServerSession);
  return <PatientDetailWorkspace key={`${props.patientId}:${session}`} {...props} />;
}

function PatientDetailWorkspace({ patientId, notice }: PatientDetailProps) {
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerSession);
  const permissions = patientPermissions(role);
  const [state, setState] = useState<State>({ key: "", status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const mutation = usePatientMutation(patientId);
  const requestKey = `${patientId}\u0000${retryToken}`;
  const retry = useCallback(() => setRetryToken((value) => value + 1), []);

  useEffect(() => {
    let active = true;
    const token = getToken();
    patientApi.getById(patientId).then((patient) => {
      if (active && token === getToken()) setState({ key: requestKey, status: "success", patient });
    }).catch((cause: unknown) => {
      if (!active || token !== getToken()) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else if (cause instanceof ApiRequestError && cause.status === 404) setState({ key: requestKey, status: "not-found" });
      else setState({ key: requestKey, status: "error", statusCode: cause instanceof ApiRequestError ? cause.status : undefined, message: cause instanceof Error ? cause.message : "Không thể tải bệnh nhân.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    });
    return () => { active = false; };
  }, [patientId, requestKey, router]);

  async function remove() {
    if (!confirmDelete || !permissions.delete || mutation.blocked) return;
    await mutation.run(() => patientApi.delete(patientId), () => router.push("/patients?notice=deleted"));
  }

  useEffect(() => {
    if (mutation.error?.status === 401) router.replace("/login");
  }, [mutation.error, router]);

  if (mutation.error) return <AsyncState kind="error" message={mutation.error.message} correlationId={mutation.error.correlationId} onRetry={() => { mutation.clearError(); setConfirmDelete(false); retry(); }} />;
  if (state.key !== requestKey) return <AsyncState kind="loading" message="Đang tải bệnh nhân…" />;
  if (state.status === "not-found") return <AsyncState kind="empty" message="Không tìm thấy bệnh nhân." />;
  if (state.status === "error") return <AsyncState kind="error" message={state.message} correlationId={state.correlationId} onRetry={retry} />;
  if (state.status !== "success") return <AsyncState kind="empty" message="Không có dữ liệu bệnh nhân." />;
  const patient = state.patient;

  return (
    <section className="mt-6 space-y-6">
      {notice ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">{notice === "created" ? "Đã tạo bệnh nhân." : "Đã cập nhật bệnh nhân."}</p> : null}
      <div className="rounded-xl border border-border bg-surface p-6"><p className="text-sm text-muted-foreground">Mã bệnh nhân</p><p className="mt-1 break-all font-mono text-sm">{patient.maBenhNhan}</p><h2 className="mt-4 text-xl font-semibold">{patient.hoTen}</h2></div>
      <dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2 lg:grid-cols-3">
        <Info label="Ngày sinh" value={formatLocalDate(patient.ngaySinh)} /><Info label="Giới tính" value={patient.gioiTinh === "M" ? "Nam" : "Nữ"} /><Info label="CMND/CCCD" value={patient.soCmnd} /><Info label="Điện thoại" value={patient.soDienThoai} /><Info label="Email" value={patient.email} /><Info label="BHYT" value={patient.bhytSo} /><Info label="Địa chỉ" value={patient.diaChi} /><Info label="Ngày tạo" value={formatInstant(patient.createdAt)} /><Info label="Cập nhật" value={formatInstant(patient.updatedAt)} />
      </dl>
      <div className="flex flex-wrap gap-2">
        {permissions.write ? <Link href={`/patients/${patientId}/edit`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary-hover">Chỉnh sửa</Link> : null}
        {permissions.delete ? <Button type="button" variant="danger" disabled={mutation.blocked} onClick={() => setConfirmDelete(true)}>Xóa bệnh nhân</Button> : null}
        <Link href="/patients" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold hover:bg-surface-muted">Quay lại danh sách</Link>
      </div>
      {confirmDelete ? <div role="alertdialog" aria-labelledby="delete-patient-title" className="rounded-xl border border-danger/40 bg-danger/10 p-5"><h2 id="delete-patient-title" className="font-semibold">Xóa hồ sơ bệnh nhân?</h2><p className="mt-1 text-sm text-muted-foreground">Thao tác này không thể hoàn tác. Backend có thể từ chối nếu hồ sơ đang được tham chiếu.</p><div className="mt-4 flex gap-2"><Button type="button" variant="danger" loading={mutation.pending} onClick={() => void remove()}>Xác nhận xóa</Button><Button type="button" disabled={mutation.pending} onClick={() => setConfirmDelete(false)}>Hủy</Button></div></div> : null}
    </section>
  );
}

function Info({ label, value }: { label: string; value: string | null }) { return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm">{value ?? "—"}</dd></div>; }
