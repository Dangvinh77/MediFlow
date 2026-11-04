"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { ApiRequestError } from "@/lib/api";
import { isRequestSessionCurrent } from "../request-session";
import { prepareCreation } from "../creation-intent";
import { roleForJobTitle } from "../account-roles";
import { organizationApi } from "../api";
import type { JobTitle, ProfileAccountDTO } from "../types";
import { AdminAccountSecurity } from "./AdminAccountSecurity";

interface ProfileAccountPanelProps {
  ownerType: "staff" | "patient";
  ownerId: string;
  profileActive?: boolean;
  jobTitle?: JobTitle;
  onProfileChanged?: () => void;
}

type LookupState = { status: "loading" } | { status: "error"; message: string } | { status: "ready"; value: ProfileAccountDTO };
const getServerValue = () => null;

export function ProfileAccountPanel(props: ProfileAccountPanelProps) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, getServerValue);
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerValue);
  return <ProfileAccountWorkspace key={`${props.ownerType}:${props.ownerId}:${session}:${role}`} {...props} role={role} />;
}

function ProfileAccountWorkspace({ ownerType, ownerId, profileActive = true, jobTitle, onProfileChanged, role }: ProfileAccountPanelProps & { role: ReturnType<typeof getRole> }) {
  const router = useRouter();
  const [attempt, setAttempt] = useState(0);
  const lookupKey = JSON.stringify([ownerType, ownerId, jobTitle, role, attempt]);
  const [settled, setSettled] = useState<{ key: string; state: LookupState } | null>(null);
  const lookup: LookupState = settled?.key === lookupKey ? settled.state : { status: "loading" };
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const alive = useRef(true);
  const commandLock = useRef(false);

  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    if (role !== "ADMIN" && !(ownerType === "patient" && role === "NURSE")) return;
    let active = true;
    const session = getSessionKey();
    organizationApi.profileAccount(ownerType, ownerId).then(value => {
      if (!active || !isRequestSessionCurrent(session)) return;
      if (!value || typeof value.emailSyncPending !== "boolean" || !(value.profileEmail === null || typeof value.profileEmail === "string")
        || !Object.prototype.hasOwnProperty.call(value, "account")) {
        throw new Error("Phản hồi tài khoản hồ sơ không hợp lệ. Chưa thể thực hiện thao tác tài khoản.");
      }
      const account = value.account;
      if (account) {
        const ownerMatches = ownerType === "patient"
          ? account.patientId === ownerId && account.staffId === null && account.role === "PATIENT"
          : account.staffId === ownerId && account.patientId === null && (!jobTitle || account.role === roleForJobTitle[jobTitle]);
        if (!ownerMatches || typeof account.accountId !== "string" || typeof account.username !== "string" || typeof account.isActive !== "boolean") {
          throw new Error("Tài khoản trả về không khớp hồ sơ chủ sở hữu. Không thể hiển thị thao tác tài khoản.");
        }
      }
      setSettled({ key: lookupKey, state: { status: "ready", value } });
    }).catch((cause: unknown) => {
      if (!active || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setSettled({ key: lookupKey, state: { status: "error", message: cause instanceof Error ? cause.message : "Không thể xác minh tài khoản hồ sơ." } });
    });
    return () => { active = false; };
  }, [lookupKey, jobTitle, ownerId, ownerType, role, router]);

  async function issue() {
    if (commandLock.current || lookup.status !== "ready" || lookup.value.account || !profileActive || !lookup.value.profileEmail || lookup.value.emailSyncPending || (ownerType === "staff" && !jobTitle)) return;
    commandLock.current = true;
    setBusy(true); setError(null);
    const session = getSessionKey();
    const isCurrent = () => alive.current && isRequestSessionCurrent(session);
    let intent: Awaited<ReturnType<typeof prepareCreation>> | undefined;
    try {
      const body = ownerType === "patient"
        ? { patientId: ownerId, purpose: "initial-patient-account" }
        : { staffId: ownerId, username: lookup.value.profileEmail, role: roleForJobTitle[jobTitle!], purpose: "initial-staff-account" };
      intent = await prepareCreation("account", session, body, ownerId);
      if (!isCurrent()) { intent.cancelUnsent(); return; }
      if (ownerType === "patient") await organizationApi.provisionPatientAccount(ownerId, intent.key);
      else await organizationApi.createAccount({ username: lookup.value.profileEmail, staffId: ownerId, patientId: null, role: roleForJobTitle[jobTitle!] }, intent.key);
      intent.settle();
      if (!isCurrent()) return;
      setAttempt(value => value + 1);
    } catch (cause) {
      intent?.settle(cause);
      if (isCurrent()) setError(cause instanceof Error ? cause.message : "Chưa xác nhận được kết quả. Thử lại để đối soát cùng yêu cầu.");
    } finally {
      if (isCurrent()) { commandLock.current = false; setBusy(false); }
    }
  }

  if (role !== "ADMIN" && !(ownerType === "patient" && role === "NURSE")) return null;
  if (lookup.status === "loading") return <section className="mt-6 rounded-xl border border-border bg-surface p-5"><h2 className="font-semibold">Tài khoản hồ sơ</h2><p role="status" className="mt-2 text-sm text-muted-foreground">Đang kiểm tra tài khoản…</p></section>;
  if (lookup.status === "error") return <section className="mt-6 space-y-3 rounded-xl border border-border bg-surface p-5"><h2 className="font-semibold">Tài khoản hồ sơ</h2><p role="alert" className="text-sm text-danger">{lookup.message}</p><Button type="button" onClick={() => setAttempt(value => value + 1)}>Thử tải lại tài khoản</Button></section>;

  const { account, profileEmail, emailSyncPending } = lookup.value;
  return <section className="mt-6 space-y-4 rounded-xl border border-border bg-surface p-5">
    <div><h2 className="text-lg font-semibold">Tài khoản hồ sơ</h2><p className="mt-1 text-sm text-muted-foreground">{profileEmail ? `Email hồ sơ: ${profileEmail}` : "Hồ sơ chưa có email để tạo hoặc khôi phục tài khoản."}</p></div>
    {emailSyncPending ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning">Email hồ sơ và email đăng nhập đang chờ đồng bộ. Hãy kiểm tra lại trước khi cấp hoặc khôi phục mật khẩu.</p> : null}
    {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
    {account ? <>
      <p className={`text-sm ${account.isActive ? "text-success" : "text-warning"}`}>{account.isActive ? "Tài khoản đang hoạt động" : "Tài khoản đã khóa"}</p>
      <p className="text-sm text-muted-foreground">Email đăng nhập: {account.username}</p>
      {!profileActive ? <p role="status" className="text-sm text-warning">Hồ sơ đã ngừng hoạt động. Các thao tác gửi mật khẩu tạm đang bị khóa.</p> : null}
      <AdminAccountSecurity key={`${ownerType}:${ownerId}:${account.accountId}:${account.username}`} accountId={account.accountId} username={account.username} patientIdForMail={ownerType === "patient" ? ownerId : undefined} allowEmailChange={role === "ADMIN"} disablePasswordActions={!profileActive || emailSyncPending || (ownerType === "patient" && !account.isActive)} onEmailChanged={() => { setAttempt(value => value + 1); onProfileChanged?.(); }} />
    </> : <>
      <p className="text-sm text-muted-foreground">{profileEmail ? "Tài khoản được tạo bằng email hồ sơ; mật khẩu ban đầu được gửi qua thư." : "Cập nhật email hồ sơ trước khi tạo tài khoản."}</p>
      <Button type="button" variant="primary" loading={busy} disabled={busy || !profileActive || !profileEmail || emailSyncPending || (ownerType === "staff" && !jobTitle)} onClick={() => void issue()}>
        {ownerType === "patient" ? "Cấp tài khoản bệnh nhân" : "Cấp tài khoản nhân sự"}
      </Button>
    </>}
  </section>;
}
