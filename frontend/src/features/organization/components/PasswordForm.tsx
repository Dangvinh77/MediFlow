"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { getSessionKey, logout } from "@/lib/auth";
import { organizationApi } from "../api";

export function PasswordForm() {
  return <PasswordWorkspace />;
}

function PasswordWorkspace() {
  const { localize } = useLocale();
  const router = useRouter();
  const alive = useRef(true), lock = useRef(false);
  const [current, setCurrent] = useState(""), [password, setPassword] = useState(""), [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false), [error, setError] = useState<string | null>(null), [done, setDone] = useState(false);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (lock.current) return;
    if (password !== confirm) { setError("Mật khẩu xác nhận không khớp."); return; }
    if (new TextEncoder().encode(password).length > 72) { setError("Mật khẩu không được quá 72 byte UTF-8."); return; }
    const session = getSessionKey();
    lock.current = true; setBusy(true); setError(null); setDone(false);
    try {
      await organizationApi.changePassword(current, password);
      if (!alive.current || getSessionKey() !== session) return;
      setCurrent(""); setPassword(""); setConfirm(""); setDone(true);
      logout(); router.replace("/login");
    } catch (cause) {
      if (alive.current && getSessionKey() === session) setError(cause instanceof Error ? cause.message : "Không thể đổi mật khẩu.");
    } finally {
      lock.current = false;
      if (alive.current && getSessionKey() === session) setBusy(false);
    }
  }
  return <form onSubmit={submit} className="mt-6 max-w-xl space-y-4 rounded-xl border border-border bg-surface p-6">
    <h2 className="text-lg font-semibold"><L10n>{"Đổi mật khẩu của bạn"}</L10n></h2>
    <p className="text-sm text-muted-foreground"><L10n>{"Sau khi đổi mật khẩu thành công, bạn sẽ đăng nhập lại."}</L10n></p>
    <L10nValue>{error ? <p role="alert" className="text-sm text-danger"><L10nValue>{error}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{done ? <p role="status" className="text-sm text-success"><L10n>{"Đã đặt lại mật khẩu."}</L10n></p> : null}</L10nValue>
    <fieldset disabled={busy} className="space-y-4">
      <Field label={localize("Mật khẩu hiện tại")} htmlFor="password-current" required><input id="password-current" type="password" autoComplete="current-password" required value={current} onChange={e => setCurrent(e.target.value)} className={controlClassName} /></Field>
      <Field label={localize("Mật khẩu mới")} htmlFor="password-new" required hint="Ít nhất 8 ký tự và tối đa 72 byte UTF-8."><input id="password-new" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} className={controlClassName} /></Field>
      <Field label={localize("Xác nhận mật khẩu mới")} htmlFor="password-confirm" required><input id="password-confirm" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={confirm} onChange={e => setConfirm(e.target.value)} className={controlClassName} /></Field>
      <Button type="submit" variant="primary" loading={busy}><L10n>{"Đổi mật khẩu"}</L10n></Button>
    </fieldset>
  </form>;
}
