"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { getSessionKey } from "@/lib/auth";
import { ApiRequestError } from "@/lib/api";
import { organizationApi } from "../api";
import { prepareCreation } from "../creation-intent";
import { isSaveOutcomeUnknown } from "../save-outcome";
import type { MailDelivery } from "../types";

const labels = { PENDING: "Đang chờ gửi", SENT: "Đã chuyển đến máy chủ email / hộp thư demo", FAILED: "Gửi email chưa thành công" };
export function AdminAccountSecurity({ accountId, username, onEmailChanged }: { accountId: string; username: string; onEmailChanged?: () => void }) {
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
    Promise.resolve().then(() => organizationApi.mailDelivery(accountId)).then(result => { if (!disposed && getSessionKey() === session && deliveryRevision.current === revision) setDelivery(result); }).catch(cause => {
      if (!disposed && getSessionKey() === session && deliveryRevision.current === revision && !(cause instanceof ApiRequestError && cause.status === 404)) setError(cause instanceof Error ? cause.message : "Chưa đọc được trạng thái gửi thư.");
    });
    return () => { disposed = true; };
  }, [accountId, attempt]);
  async function action(kind: "reset" | "retry" | "email" | "checkEmail") {
    if (lock.current || (unknown && kind !== "reset") || (emailUnknown && kind !== "checkEmail")) return;
    if (kind === "reset" && !unknown && !window.confirm(`Cấp mật khẩu tạm mới và gửi email cho ${username}? Mật khẩu hiện tại sẽ hết hiệu lực.`)) return;
    const session = getSessionKey();
    const current = () => alive.current && getSessionKey() === session;
    lock.current = true; setBusy(true); setError(null); setNotice(null);
    deliveryRevision.current += 1;
    let intent: Awaited<ReturnType<typeof prepareCreation>> | undefined;
    try {
      if (kind === "reset") {
        intent = await prepareCreation("account", session, { accountId, purpose: "admin-password-reset" }, `password:${accountId}`);
        if (!current()) { intent.cancelUnsent(); return; }
        const result = await organizationApi.resetPassword(accountId, intent.key);
        intent.settle();
        if (current()) { setDelivery(result); setUnknown(false); setNotice("Đã cấp mật khẩu tạm. Người dùng sẽ nhận hướng dẫn qua email và phải đổi mật khẩu khi đăng nhập."); }
      } else if (kind === "retry" && delivery) {
        const result = await organizationApi.retryMail(accountId, delivery.messageId);
        if (current()) { setDelivery(result); setNotice("Đã yêu cầu gửi lại đúng thư này; mật khẩu không bị đặt lại lần nữa."); }
      } else if (kind === "checkEmail") {
        const result = await organizationApi.account(accountId);
        if (current()) {
          if (result.username.toLowerCase() === email.trim().toLowerCase()) {
            setEmailUnknown(false); setNotice("Đã xác nhận email đăng nhập mới."); onEmailChanged?.();
          } else setNotice(`Email hiện tại là ${result.username}. Chưa xác nhận thay đổi; hãy kiểm tra lại sau.`);
        }
      } else if (kind === "email") {
        await organizationApi.updateAccountEmail(accountId, email.trim(), reason.trim());
        if (current()) { setReason(""); setNotice("Đã cập nhật email đăng nhập. Các phiên và link khôi phục cũ đã bị vô hiệu hóa."); onEmailChanged?.(); }
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
  function submitEmail(event: FormEvent) { event.preventDefault(); if (email.trim() !== username && reason.trim()) void action("email"); }
  return <section className="space-y-5 rounded-xl border border-border bg-surface p-5 sm:p-6">
    <h2 className="text-lg font-semibold">Bảo mật & gửi email tài khoản</h2>
    <p className="text-sm leading-6 text-muted-foreground">Sau khi xác minh người yêu cầu, bạn có thể cấp mật khẩu tạm và gửi hướng dẫn đến email đăng nhập. Email giả của seed được chuyển vào hộp thư demo.</p>
    {delivery ? <p role="status" className="rounded-lg bg-surface-muted p-3 text-sm">Trạng thái thư gần nhất: <strong>{labels[delivery.status]}</strong></p> : null}
    {notice ? <p role="status" className="text-sm leading-6 text-success">{notice}</p> : null}
    {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
    <div className="flex flex-wrap gap-3"><Button type="button" variant="primary" disabled={emailUnknown} loading={busy} onClick={() => void action("reset")}>{unknown ? "Đối soát lần cấp mật khẩu vừa rồi" : "Cấp mật khẩu tạm qua email"}</Button><Button type="button" disabled={busy} onClick={() => { setError(null); setAttempt(v => v + 1); }}>Kiểm tra trạng thái thư</Button>{delivery && delivery.status !== "SENT" ? <Button type="button" disabled={busy || unknown || emailUnknown} onClick={() => void action("retry")}>Gửi lại thư</Button> : null}</div>
    {emailUnknown ? <div className="space-y-3"><p className="text-sm text-warning">Chưa xác nhận kết quả đổi email. Kiểm tra email hiện tại trước khi tiếp tục.</p><Button type="button" loading={busy} onClick={() => void action("checkEmail")}>Kiểm tra kết quả đổi email</Button></div> : null}
    {unknown ? <p className="text-sm text-warning">Chưa xác nhận lần cấp mật khẩu vừa rồi. Dùng nút đối soát để tiếp tục cùng yêu cầu; không tạo thêm lần đặt lại.</p> : null}
    <form onSubmit={submitEmail} className="space-y-4 border-t border-border pt-5">
      <h3 className="font-semibold">Đổi email đăng nhập</h3><p className="text-sm text-muted-foreground">Chỉ quản trị viên được thay đổi. Kiểm tra kỹ người sở hữu trước khi lưu.</p>
      <fieldset disabled={busy || unknown || emailUnknown} className="grid gap-4 sm:grid-cols-2"><Field label="Email đăng nhập mới" htmlFor="admin-account-email"><input id="admin-account-email" type="email" required maxLength={254} autoComplete="off" value={email} onChange={e => setEmail(e.target.value)} className={controlClassName} /></Field><Field label="Lý do thay đổi email" htmlFor="admin-email-reason"><input id="admin-email-reason" required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} className={controlClassName} /></Field><div><Button type="submit" disabled={email.trim() === username || !reason.trim()} loading={busy}>Lưu email mới</Button></div></fieldset>
    </form>
  </section>;
}
