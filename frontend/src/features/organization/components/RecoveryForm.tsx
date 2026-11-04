"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { accountSecurityApi } from "../security-api";

export function RecoveryForm({ token }: { token?: string }) {
  const reset = token !== undefined;
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const lock = useRef(false), alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (lock.current || done) return;
    setError(null);
    if (reset && !token) { setError("Link không hợp lệ. Hãy yêu cầu một link mới."); return; }
    if (reset && password !== confirm) { setError("Mật khẩu xác nhận không khớp."); return; }
    if (reset && (password.length < 8 || new TextEncoder().encode(password).length > 72)) {
      setError("Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8."); return;
    }
    lock.current = true; setBusy(true);
    try {
      if (reset) await accountSecurityApi.reset(token!, password);
      else await accountSecurityApi.forgot(email.trim());
      if (alive.current) { setDone(true); setPassword(""); setConfirm(""); }
    } catch (cause) {
      if (alive.current) setError(cause instanceof Error ? cause.message : "Chưa thể thực hiện. Vui lòng thử lại.");
    } finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  return <main className="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6 py-12">
    <Link href="/login" className="mb-8 flex items-center gap-3 text-xl font-semibold"><span className="grid size-11 place-items-center rounded-xl bg-primary text-sm text-primary-foreground">MF</span>MediFlow</Link>
    <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm sm:p-8">
      <h1 className="text-2xl font-semibold">{reset ? "Đặt mật khẩu mới" : "Quên mật khẩu"}</h1>
      <p className="mt-3 text-sm leading-6 text-muted-foreground">{reset ? "Link có hiệu lực trong 30 phút và chỉ dùng một lần. Sau khi lưu, hãy đăng nhập bằng mật khẩu mới." : "Nhập email đăng nhập để nhận hướng dẫn khôi phục tài khoản. Bạn không cần tạo lại hồ sơ."}</p>
      {reset && !token ? <p role="alert" className="mt-4 text-sm text-danger">Link không hợp lệ. Hãy chọn “Yêu cầu link mới” bên dưới.</p> : null}
      {done ? <p role="status" className="mt-6 rounded-xl bg-success/10 p-4 text-sm leading-6 text-success">{reset ? "Đã đổi mật khẩu. Vui lòng đăng nhập lại; các phiên cũ đã bị vô hiệu hóa." : "Nếu email thuộc tài khoản đủ điều kiện, hệ thống sẽ gửi link đặt lại mật khẩu. Hãy kiểm tra cả thư rác; tài khoản seed nhận thư trong Mailbox demo."}</p> : <form onSubmit={submit} className="mt-6 space-y-5">
        <fieldset disabled={busy} className="space-y-5">
          {reset ? <>
            <Field label="Mật khẩu mới" htmlFor="recovery-password" hint="Ít nhất 8 ký tự, tối đa 72 byte UTF-8."><input id="recovery-password" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} className={controlClassName} /></Field>
            <Field label="Xác nhận mật khẩu" htmlFor="recovery-confirm"><input id="recovery-confirm" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={confirm} onChange={e => setConfirm(e.target.value)} className={controlClassName} /></Field>
          </> : <Field label="Email" htmlFor="recovery-email"><input id="recovery-email" type="email" autoComplete="email" required maxLength={254} value={email} onChange={e => setEmail(e.target.value)} className={controlClassName} /></Field>}
          {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
          <Button type="submit" variant="primary" loading={busy} disabled={reset && !token}>{reset ? "Lưu mật khẩu mới" : "Gửi link đặt lại mật khẩu"}</Button>
        </fieldset>
      </form>}
      <div className="mt-6 flex flex-wrap gap-5 text-sm font-medium text-primary"><Link href="/login" className="underline">Đăng nhập</Link>{reset ? <Link href="/forgot-password" className="underline">Yêu cầu link mới</Link> : null}</div>
    </section>
  </main>;
}
