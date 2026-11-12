"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { getSessionKey } from "@/lib/auth";
import { ApiRequestError } from "@/lib/api";
import { organizationApi } from "../api";
import { prepareCreation } from "../creation-intent";
import { isSaveOutcomeUnknown } from "../save-outcome";
import type { MailDelivery } from "../types";

const labels = { PENDING: "Đang gửi…", SENT: "Gửi thành công", FAILED: "Gửi thất bại" };
const statusTone = { PENDING: "bg-surface-muted text-muted-foreground", SENT: "bg-success/10 text-success", FAILED: "bg-danger/10 text-danger" };
// While the outbox still holds the mail, re-read its status on its own: fast at first, then slower.
const FAST_POLLS = 15, FAST_POLL_MS = 2000, SLOW_POLL_MS = 10000;
interface AdminAccountSecurityProps {
  accountId: string;
  username: string;
  patientIdForMail?: string;
  allowEmailChange?: boolean;
  emailSyncPending?: boolean;
  disablePasswordActions?: boolean;
  onEmailChanged?: () => void;
}

export function AdminAccountSecurity({ accountId, username, patientIdForMail, allowEmailChange = true, emailSyncPending = false, disablePasswordActions = false, onEmailChanged }: AdminAccountSecurityProps) {
  const { localize } = useLocale();
  const alive = useRef(true), lock = useRef(false);
  const [delivery, setDelivery] = useState<MailDelivery | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [unknown, setUnknown] = useState(false);
  const [emailUnknown, setEmailUnknown] = useState(false);
  const deliveryRevision = useRef(0);
  const [email, setEmail] = useState(username);
  const [reason, setReason] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    let disposed = false;
    const session = getSessionKey();
    const revision = deliveryRevision.current;
    Promise.resolve().then(() => patientIdForMail
      ? organizationApi.patientMailDelivery(patientIdForMail)
      : organizationApi.mailDelivery(accountId)).then(result => { if (!disposed && getSessionKey() === session && deliveryRevision.current === revision) setDelivery(result); }).catch(cause => {
      if (!disposed && getSessionKey() === session && deliveryRevision.current === revision && !(cause instanceof ApiRequestError && cause.status === 404)) setError(cause instanceof Error ? cause.message : "Chưa đọc được trạng thái gửi thư.");
    });
    return () => { disposed = true; };
  }, [accountId, attempt, patientIdForMail]);
  const polls = useRef(0);
  const [stalled, setStalled] = useState(false);
  useEffect(() => {
    if (delivery?.status !== "PENDING") { polls.current = 0; return; }
    const timer = window.setTimeout(() => {
      polls.current += 1;
      if (polls.current >= FAST_POLLS) setStalled(true);
      setAttempt(v => v + 1);
    }, polls.current < FAST_POLLS ? FAST_POLL_MS : SLOW_POLL_MS);
    return () => window.clearTimeout(timer);
  }, [delivery]);
  async function action(kind: "reset" | "retry" | "email" | "checkEmail") {
    if (lock.current || (disablePasswordActions && (kind === "reset" || kind === "retry")) || (unknown && kind !== "reset") || (emailUnknown && kind !== "checkEmail") || (!allowEmailChange && (kind === "email" || kind === "checkEmail"))) return;
    if (kind === "reset" && !unknown && !window.confirm(`Cấp lại mật khẩu tạm và gửi email cho ${username}? Mật khẩu hiện tại sẽ hết hiệu lực.`)) return;
    const session = getSessionKey();
    const current = () => alive.current && getSessionKey() === session;
    lock.current = true; setBusy(true); setError(null); setNotice(null);
    deliveryRevision.current += 1;
    if (kind === "reset" || kind === "retry") { polls.current = 0; setStalled(false); }
    let intent: Awaited<ReturnType<typeof prepareCreation>> | undefined;
    try {
      if (kind === "reset") {
        intent = await prepareCreation("account", session, patientIdForMail
          ? { patientId: patientIdForMail, purpose: "patient-password-reset" }
          : { accountId, purpose: "admin-password-reset" }, `password:${patientIdForMail ?? accountId}`);
        if (!current()) { intent.cancelUnsent(); return; }
        const result = patientIdForMail
          ? await organizationApi.resetPatientPassword(patientIdForMail, intent.key)
          : await organizationApi.resetPassword(accountId, intent.key);
        intent.settle();
        if (current()) { setDelivery(result); setUnknown(false); setNotice("Đã cấp lại mật khẩu tạm. Người dùng sẽ nhận hướng dẫn qua email và phải đổi mật khẩu khi đăng nhập."); }
      } else if (kind === "retry" && delivery) {
        const result = patientIdForMail
          ? await organizationApi.retryPatientMail(patientIdForMail, delivery.messageId)
          : await organizationApi.retryMail(accountId, delivery.messageId);
        if (current()) { setDelivery(result); setNotice("Đã yêu cầu gửi lại đúng thư này; mật khẩu không bị đặt lại lần nữa."); }
      } else if (kind === "checkEmail") {
        const profile = patientIdForMail
          ? await organizationApi.profileAccount("patient", patientIdForMail)
          : null;
        const result = patientIdForMail ? profile?.account : await organizationApi.account(accountId);
        if (patientIdForMail && (!profile || typeof profile.emailSyncPending !== "boolean")) {
          throw new Error("Chưa xác minh được trạng thái đồng bộ của hồ sơ bệnh nhân. Vui lòng tải lại hồ sơ.");
        }
        if (!result || (patientIdForMail && (result.accountId !== accountId || result.patientId !== patientIdForMail || result.staffId !== null || result.role !== "PATIENT"))) {
          throw new Error("Chưa xác minh được tài khoản của bệnh nhân. Vui lòng tải lại hồ sơ.");
        }
        if (current()) {
          if (result.username.toLowerCase() === email.trim().toLowerCase()) {
            setEmailUnknown(false);
            if (patientIdForMail && profile?.emailSyncPending) {
              setNotice("Email đăng nhập đã khớp, nhưng hồ sơ vẫn chờ đồng bộ. Có thể lưu lại cùng email để gửi yêu cầu đồng bộ.");
            } else {
              setReason("");
              setNotice("Đã xác nhận email đăng nhập mới.");
            }
            onEmailChanged?.();
          } else setNotice(`Email hiện tại là ${result.username}. Chưa xác nhận thay đổi; hãy kiểm tra lại sau.`);
        }
      } else if (kind === "email") {
        if (patientIdForMail) await organizationApi.updatePatientEmail(patientIdForMail, email.trim(), reason.trim());
        else await organizationApi.updateAccountEmail(accountId, email.trim(), reason.trim());
        if (current()) { setReason(""); setNotice(email.trim().toLowerCase() === username.toLowerCase() ? "Đã yêu cầu đồng bộ email hồ sơ theo email đăng nhập hiện tại." : "Đã cập nhật email đăng nhập. Các phiên và link khôi phục cũ đã bị vô hiệu hóa."); onEmailChanged?.(); }
      }
    } catch (cause) {
      intent?.settle(cause);
      if (current()) {
        const uncertain = isSaveOutcomeUnknown(cause);
        if (kind === "reset") setUnknown(uncertain);
        if (kind === "email") setEmailUnknown(uncertain);
        setError(cause instanceof Error ? cause.message : "Chưa xác nhận được kết quả. Vui lòng kiểm tra lại.");
      }
    } finally { lock.current = false; if (current()) setBusy(false); }
  }
  const unchangedEmail = email.trim().toLowerCase() === username.toLowerCase();
  function submitEmail(event: FormEvent) { event.preventDefault(); if ((!unchangedEmail || emailSyncPending) && reason.trim()) void action("email"); }
  return <section className="space-y-5 rounded-xl border border-border bg-surface p-5 sm:p-6">
    <h2 className="text-lg font-semibold"><L10n>{"Bảo mật & gửi email tài khoản"}</L10n></h2>
    <p className="text-sm leading-6 text-muted-foreground"><L10n>{"Sau khi xác minh người yêu cầu, bạn có thể cấp mật khẩu tạm và gửi hướng dẫn đến email đăng nhập. Email giả của seed được chuyển vào hộp thư demo."}</L10n></p>
    <L10nValue>{delivery ? <p role="status" aria-live="polite" className={`rounded-lg p-3 text-sm ${statusTone[delivery.status]}`}><L10n>{"Thư mật khẩu tạm: "}</L10n><strong><L10nValue>{labels[delivery.status]}</L10nValue></strong><L10nValue>{delivery.status === "PENDING" && stalled ? " — máy chủ email phản hồi chậm, có thể gửi lại thư." : null}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{notice ? <p role="status" className="text-sm leading-6 text-success"><L10nValue>{notice}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{error ? <p role="alert" className="text-sm text-danger"><L10nValue>{error}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{disablePasswordActions ? <p role="status" className="text-sm text-warning"><L10n>{"Tạm khóa cấp lại mật khẩu và gửi lại thư khi hồ sơ/email chưa thể xác minh."}</L10n></p> : null}</L10nValue>
    <div className="flex flex-wrap gap-3"><Button type="button" variant="primary" disabled={disablePasswordActions || emailUnknown} loading={busy} onClick={() => void action("reset")}><L10nValue>{unknown ? "Đối soát lần cấp lại mật khẩu vừa rồi" : "Cấp lại mật khẩu tạm qua email"}</L10nValue></Button><L10nValue>{delivery && (delivery.status === "FAILED" || (delivery.status === "PENDING" && stalled)) ? <Button type="button" disabled={busy || disablePasswordActions || unknown || emailUnknown} onClick={() => void action("retry")}><L10n>{"Gửi lại thư"}</L10n></Button> : null}</L10nValue></div>
    <L10nValue>{emailUnknown ? <div className="space-y-3"><p className="text-sm text-warning"><L10n>{"Chưa xác nhận kết quả đổi email. Kiểm tra email hiện tại trước khi tiếp tục."}</L10n></p><Button type="button" loading={busy} onClick={() => void action("checkEmail")}><L10n>{"Kiểm tra kết quả đổi email"}</L10n></Button></div> : null}</L10nValue>
    <L10nValue>{unknown ? <p className="text-sm text-warning"><L10n>{"Chưa xác nhận lần cấp mật khẩu vừa rồi. Dùng nút đối soát để tiếp tục cùng yêu cầu; không tạo thêm lần đặt lại."}</L10n></p> : null}</L10nValue>
    <L10nValue>{allowEmailChange ? <form onSubmit={submitEmail} className="space-y-4 border-t border-border pt-5">
      <h3 className="font-semibold"><L10n>{"Đổi email đăng nhập"}</L10n></h3><p className="text-sm text-muted-foreground"><L10nValue>{patientIdForMail ? "Điều dưỡng hoặc quản trị viên có thể sửa email bệnh nhân sau khi xác minh. Email hồ sơ sẽ được đồng bộ." : "Chỉ quản trị viên được đổi email tại trang quản lý tài khoản."}</L10nValue><L10n>{" Kiểm tra kỹ người sở hữu trước khi lưu."}</L10n></p>
      <fieldset disabled={busy || unknown || emailUnknown} className="grid gap-4 sm:grid-cols-2"><Field label={localize("Email đăng nhập mới")} htmlFor="admin-account-email"><input id="admin-account-email" type="email" required maxLength={254} autoComplete="off" value={email} onChange={e => setEmail(e.target.value)} className={controlClassName} /></Field><Field label={localize("Lý do thay đổi email")} htmlFor="admin-email-reason"><input id="admin-email-reason" required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} className={controlClassName} /></Field><div><Button type="submit" disabled={(unchangedEmail && !emailSyncPending) || !reason.trim()} loading={busy}><L10n>{"Lưu email mới"}</L10n></Button></div></fieldset>
    </form> : null}</L10nValue>
  </section>;
}
