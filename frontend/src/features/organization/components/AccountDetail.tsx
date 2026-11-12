"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { getSessionKey } from "@/lib/auth";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { organizationApi } from "../api";
import { accountRoleLabels } from "../account-roles";
import { isRequestSessionCurrent } from "../request-session";
import type { AccountDTO } from "../types";
import { DetailSection, RecordIdentity } from "./RecordLayout";
import { AdminAccountSecurity } from "./AdminAccountSecurity";
import { AccountStaffLink } from "./AccountStaffLink";

/** Route composition keys this component by account and signed-in session. */
export function AccountDetail({ accountId, renderPatientLink }: { accountId: string; renderPatientLink?: (patientId: string) => ReactNode }) {
  const { localize } = useLocale();
  const router = useRouter();
  const mounted = useRef(true);
  const submitting = useRef(false);
  const [account, setAccount] = useState<AccountDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [confirmation, setConfirmation] = useState<string | null>(null);
  const [unknown, setUnknown] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    let disposed = false;
    const session = getSessionKey();
    organizationApi.account(accountId).then(value => {
      if (!disposed && getSessionKey() === session) { setAccount(value); setUnknown(false); }
    }).catch((cause: unknown) => {
      if (disposed || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setError(cause instanceof Error ? cause.message : "Không tải được tài khoản.");
    });
    return () => { disposed = true; };
  }, [accountId, attempt, router]);

  function reload() { setError(null); setAccount(null); setConfirmation(null); setSuccess(null); setAttempt(value => value + 1); }
  async function updateStatus(isActive: boolean, replace = false) {
    if (submitting.current || unknown) return;
    const session = getSessionKey();
    submitting.current = true; setBusy(true); setError(null); setSuccess(null);
    try {
      const value = await organizationApi.updateAccountStatus(accountId, isActive, replace);
      if (!mounted.current || getSessionKey() !== session) return;
      setAccount(value); setConfirmation(null);
      setSuccess(`Đã chuyển tài khoản sang ${value.isActive ? "active" : "inactive"}.`);
    } catch (cause: unknown) {
      if (!mounted.current || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      if (cause instanceof ApiRequestError && cause.status === 409 && cause.code?.endsWith("ACCOUNT_OWNER_ACTIVE")) setConfirmation(cause.message);
      else {
        setConfirmation(null);
        const uncertain = !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
        setUnknown(uncertain);
        setError(uncertain ? "Chưa xác nhận được kết quả cập nhật. Hãy tải lại trạng thái trước khi thực hiện tiếp." : cause.message);
      }
    } finally { submitting.current = false; if (mounted.current && getSessionKey() === session) setBusy(false); }
  }

  if (!account) return <div className="mt-6"><L10nValue>{error ? <AsyncState kind="error" message={localize(error)} onRetry={reload} /> : <AsyncState kind="loading" message={localize("Đang tải chi tiết tài khoản…")} />}</L10nValue></div>;
  return <section className="mt-6 space-y-5">
    <RecordIdentity name={account.username} subtitle={accountRoleLabels[account.role]} />
    <DetailSection title={localize("Chi tiết tài khoản")}>
      <Info label={localize("Email đăng nhập")} value={account.username} /><Info label={localize("Vai trò")} value={accountRoleLabels[account.role]} />
      <Info label={localize("Trạng thái")} value={account.isActive ? "Active — Đang hoạt động" : "Inactive — Đã khóa"} />
      <div><dt className="text-sm text-muted-foreground"><L10n>{"Hồ sơ liên kết"}</L10n></dt><dd className="mt-1.5 break-words text-sm leading-6"><L10nValue>{account.staffId ? <AccountStaffLink key={account.staffId} staffId={account.staffId} /> : account.patientId ? renderPatientLink?.(account.patientId) ?? <Link className="text-primary underline" href={`/patients/${account.patientId}`}><L10n>{"Bệnh nhân: Xem hồ sơ"}</L10n></Link> : "Chưa liên kết"}</L10nValue></dd></div>
      <Info label={localize("Mã tài khoản")} value={account.accountId} /><Info label={localize("Lần đăng nhập gần nhất")} value={formatInstant(account.lastLoginAt)} />
      <Info label={localize("Ngày tạo")} value={formatInstant(account.createdAt)} /><Info label={localize("Cập nhật lần cuối")} value={formatInstant(account.updatedAt)} />
    </DetailSection>
    <div className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="font-semibold"><L10n>{"Trạng thái truy cập"}</L10n></h2><p className="text-sm text-muted-foreground"><L10n>{"Tài khoản inactive không thể đăng nhập hoặc gia hạn phiên. Phiên truy cập đã cấp sẽ bị từ chối ngay ở yêu cầu tiếp theo sau khi tài khoản bị khóa."}</L10n></p><Button type="button" variant="primary" disabled={unknown || confirmation !== null} loading={busy} onClick={() => void updateStatus(!account.isActive)}><L10nValue>{account.isActive ? "Inactive tài khoản" : "Active tài khoản"}</L10nValue></Button></div>
    <L10nValue>{confirmation ? <div role="alertdialog" aria-modal="false" aria-labelledby="status-confirm-title" aria-describedby="status-confirm-message" className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 id="status-confirm-title" className="font-semibold"><L10n>{"Xác nhận active tài khoản"}</L10n></h2><p id="status-confirm-message"><L10nValue>{confirmation}</L10nValue></p><div className="flex flex-wrap gap-3"><Button type="button" disabled={busy} onClick={() => setConfirmation(null)}><L10n>{"Hủy"}</L10n></Button><Button type="button" loading={busy} onClick={() => void updateStatus(true, true)}><L10n>{"Tiếp tục active tài khoản"}</L10n></Button></div></div> : null}</L10nValue>
    <L10nValue>{error ? <div role="alert" className="space-y-3 text-sm text-danger"><p><L10nValue>{error}</L10nValue></p><L10nValue>{unknown ? <Button type="button" onClick={reload}><L10n>{"Tải lại trạng thái"}</L10n></Button> : null}</L10nValue></div> : null}</L10nValue>
    <L10nValue>{success ? <p role="status" className="text-sm text-success"><L10nValue>{success}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{account.role !== "SYSTEM" && !busy && !unknown && !confirmation ? <AdminAccountSecurity key={`${account.accountId}:${account.username}`} accountId={account.accountId} username={account.username} onEmailChanged={reload} /> : null}</L10nValue>
  </section>;
}

function Info({ label, value }: { label: string; value: string }) { return <div><dt className="text-sm text-muted-foreground"><L10nValue>{label}</L10nValue></dt><dd className="mt-1.5 break-words text-sm leading-6">{value}</dd></div>; }
