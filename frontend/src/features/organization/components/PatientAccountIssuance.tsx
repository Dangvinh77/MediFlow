"use client";

import Link from "next/link";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { Button } from "@/components/ui/Button";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import { organizationApi } from "../api";
import { prepareCreation } from "../creation-intent";
import type { IssuedPatientAccount } from "../types";

export function PatientAccountIssuance({ patientId }: { patientId: string }) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <IssuanceWorkspace key={`${session}:${patientId}`} patientId={patientId} />;
}

function IssuanceWorkspace({ patientId }: { patientId: string }) {
  const [issued, setIssued] = useState<IssuedPatientAccount | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false), alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  async function provision() {
    if (lock.current || issued) return;
    lock.current = true; setBusy(true); setError(null);
    const session = getSessionKey();
    let intent: Awaited<ReturnType<typeof prepareCreation>> | undefined;
    const current = () => alive.current && getSessionKey() === session;
    try {
      intent = await prepareCreation("account", session, { patientId, purpose: "initial-patient-account" }, patientId);
      if (!current()) { intent.cancelUnsent(); return; }
      const result = await organizationApi.provisionPatientAccount(patientId, intent.key);
      intent.settle();
      if (current()) setIssued(result);
    } catch (cause) {
      intent?.settle(cause);
      if (current()) setError(cause instanceof Error ? cause.message : "Chưa thể cấp tài khoản. Thử lại để đối soát cùng yêu cầu.");
    } finally {
      if (current()) { setBusy(false); lock.current = false; }
    }
  }
  return <section className="mt-6 space-y-4 rounded-xl border border-border bg-surface p-5">
    <h2 className="text-lg font-semibold">Tài khoản bệnh nhân và đăng ký khám</h2>
    <p className="text-sm text-muted-foreground">Hồ sơ đã được lưu. Bệnh nhân chưa cần đăng nhập để tiếp tục khám. Khi muốn xem kết quả khám, xét nghiệm hoặc thông báo, bệnh nhân đăng nhập và đổi mật khẩu ban đầu một lần.</p>
    {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
    {issued ? <div role="status" className="space-y-1 text-sm">
      <p>Đã cấp tài khoản. Tên đăng nhập: <strong>{issued.username}</strong></p>
      {issued.mustChangePassword
        ? <p>Mật khẩu ban đầu: <strong>1</strong>. Giao thông tin trực tiếp cho đúng bệnh nhân.</p>
        : <p>Mật khẩu đã được bệnh nhân đổi. Tiếp tục dùng mật khẩu hiện tại; yêu cầu này không đặt lại mật khẩu.</p>}
    </div> : <Button type="button" loading={busy} onClick={() => void provision()}>Cấp tài khoản bệnh nhân</Button>}
    <Link className="block text-sm font-medium text-primary underline" href={`/appointments/reception?patientId=${encodeURIComponent(patientId)}`}>Tiếp tục đăng ký khám</Link>
    <p className="text-sm text-muted-foreground">Nếu đã có tài khoản, dùng tài khoản cũ; quên mật khẩu cần phục hồi sau xác minh danh tính.</p>
  </section>;
}
