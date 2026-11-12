"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/auth";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import { formatLocalDate } from "@/lib/format";
import { DetailSection, RecordIdentity } from "./RecordLayout";
import { patientApi } from "../api";
import { patientPermissions } from "../logic";
import type { PatientDTO } from "../types";
import { usePatientMutation } from "../usePatientMutation";

interface PatientDetailProps { patientId: string; notice?: "created" | "updated"; onboarding?: ReactNode }
type State = { key: string; status: "idle" } | { key: string; status: "success"; patient: PatientDTO } | { key: string; status: "not-found" } | { key: string; status: "error"; statusCode?: number; message: string; correlationId: string | null };
const getServerSession = () => null;

export function PatientDetail(props: PatientDetailProps) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, getServerSession);
  return <PatientDetailWorkspace key={`${props.patientId}:${session}`} {...props} />;
}

function PatientDetailWorkspace({ patientId, notice, onboarding }: PatientDetailProps) {
  const { localize } = useLocale();
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerSession);
  const permissions = patientPermissions(role);
  const [state, setState] = useState<State>({ key: "", status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const mutation = usePatientMutation(patientId);
  const deleteTrigger = useRef<HTMLButtonElement>(null);
  const deletePanel = useRef<HTMLDivElement>(null);

  useEffect(() => { if (confirmDelete) deletePanel.current?.focus(); }, [confirmDelete]);
  function cancelDelete() {
    if (mutation.pending) return;
    setConfirmDelete(false);
    deleteTrigger.current?.focus();
  }
  const requestKey = `${patientId}\u0000${retryToken}`;
  const retry = useCallback(() => setRetryToken((value) => value + 1), []);

  useEffect(() => {
    let active = true;
    const token = getSessionKey();
    patientApi.getById(patientId).then((patient) => {
      if (active && token === getSessionKey()) setState({ key: requestKey, status: "success", patient });
    }).catch((cause: unknown) => {
      if (!active || token !== getSessionKey()) return;
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

  if (mutation.error) return <AsyncState kind="error" message={localize(mutation.error.message)} correlationId={mutation.error.correlationId} onRetry={() => { mutation.clearError(); setConfirmDelete(false); retry(); }} />;
  if (state.key !== requestKey) return <AsyncState kind="loading" message={localize("Đang tải bệnh nhân…")} />;
  if (state.status === "not-found") return <AsyncState kind="empty" message={localize("Không tìm thấy bệnh nhân.")} />;
  if (state.status === "error") return <AsyncState kind="error" message={localize(state.message)} correlationId={state.correlationId} onRetry={retry} />;
  if (state.status !== "success") return <AsyncState kind="empty" message={localize("Không có dữ liệu bệnh nhân.")} />;
  const patient = state.patient;

  return (
    <section className="mt-6 space-y-6">
      <L10nValue>{patient.daLuuTru ? <p role="status" className="rounded-lg border border-border bg-surface-muted p-4 text-sm"><L10n>{"Hồ sơ đã lưu trữ. Bạn vẫn có thể xem thông tin để đối chiếu lịch sử."}</L10n></p> : null}</L10nValue>
      <L10nValue>{notice ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success"><L10nValue>{notice === "created" ? "Đã tạo bệnh nhân." : "Đã cập nhật bệnh nhân."}</L10nValue></p> : null}</L10nValue>
      <RecordIdentity name={patient.hoTen} subtitle={`${patient.gioiTinh === "M" ? "Nam" : "Nữ"} · ${formatLocalDate(patient.ngaySinh)}`}><L10nValue>{permissions.write && !patient.daLuuTru ? <Link href={`/patients/${patientId}/edit`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary-hover"><L10n>{"Chỉnh sửa"}</L10n></Link> : null}</L10nValue></RecordIdentity>
      <div className="grid items-start gap-5 xl:grid-cols-2">
        <DetailSection title={localize("Thông tin cá nhân")}><Info label={localize("Ngày sinh")} value={formatLocalDate(patient.ngaySinh)} /><Info label={localize("Giới tính")} value={patient.gioiTinh === "M" ? "Nam" : "Nữ"} /><Info label="CMND/CCCD" value={patient.soCmnd} /><Info label={localize("BHYT")} value={patient.bhytSo} /></DetailSection>
        <DetailSection title={localize("Thông tin liên hệ")}><Info label={localize("Điện thoại")} value={patient.soDienThoai} /><Info label={localize("Email")} value={patient.email} /><Info label={localize("Địa chỉ")} value={patient.diaChi} /></DetailSection>
      </div>
      <L10nValue>{permissions.write && !patient.daLuuTru ? onboarding : null}</L10nValue>
      <div className="flex flex-wrap gap-2">
        <L10nValue>{permissions.delete && !patient.daLuuTru ? <button type="button" ref={deleteTrigger} disabled={mutation.blocked} onClick={() => setConfirmDelete(true)} className="inline-flex min-h-11 items-center justify-center rounded-lg border border-danger/40 bg-surface px-4 py-2 text-sm font-semibold text-danger hover:bg-danger/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-55"><L10n>{"Lưu trữ bệnh nhân"}</L10n></button> : null}</L10nValue>
        <Link href="/patients" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold hover:bg-surface-muted"><L10n>{"Quay lại danh sách"}</L10n></Link>
      </div>
      <L10nValue>{confirmDelete ? <div role="alertdialog" ref={deletePanel} tabIndex={-1} aria-labelledby="delete-patient-title" aria-describedby="delete-patient-description" onKeyDown={(event) => { if (event.key === "Escape") { event.preventDefault(); cancelDelete(); } }} className="rounded-xl border border-danger/40 bg-danger/10 p-5"><h2 id="delete-patient-title" className="font-semibold"><L10n>{"Lưu trữ hồ sơ bệnh nhân?"}</L10n></h2><p id="delete-patient-description" className="mt-1 text-sm text-muted-foreground"><L10n>{"Hồ sơ của "}</L10n>{patient.hoTen}<L10n>{" sẽ được ẩn khỏi danh sách tiếp nhận. Mã bệnh nhân và dữ liệu được giữ lại để tra cứu lịch sử; thao tác này không xóa hồ sơ khám hoặc giao dịch."}</L10n></p><div className="mt-4 flex gap-2"><Button type="button" variant="danger" loading={mutation.pending} onClick={() => void remove()}><L10n>{"Xác nhận lưu trữ"}</L10n></Button><Button type="button" disabled={mutation.pending} onClick={cancelDelete}><L10n>{"Hủy"}</L10n></Button></div></div> : null}</L10nValue>
    </section>
  );
}

function Info({ label, value }: { label: string; value: string | null }) { return <div><dt className="text-sm text-muted-foreground"><L10nValue>{label}</L10nValue></dt><dd className="mt-1.5 break-words text-sm leading-6"><L10nValue>{value ?? "—"}</L10nValue></dd></div>; }
