"use client";

import Link from "next/link";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { controlClassName, Field } from "@/components/ui/Field";
import { formatInstant } from "@/lib/format";
import type { PageResult } from "@/lib/types";
import { accountRoleLabels } from "../account-roles";
import { accountSecurityApi, type MailboxAccount, type MailboxDetail, type MailboxMessage } from "../security-api";
import { safeMailHtml } from "../mail-html";

export function DemoMailbox() {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [configError, setConfigError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let disposed = false;
    accountSecurityApi.demoConfig().then(value => { if (!disposed) setEnabled(value.enabled); }).catch(cause => { if (!disposed) setConfigError(cause instanceof Error ? cause.message : "Không tải được cấu hình."); });
    return () => { disposed = true; };
  }, [attempt]);
  return <main className="mx-auto max-w-7xl px-4 py-8 sm:px-8">
    <header className="mb-8 flex flex-wrap items-start justify-between gap-4"><div><p className="text-sm font-medium text-primary">MediFlow · Môi trường demo</p><h1 className="mt-2 text-3xl font-semibold">Hộp thư demo</h1><p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">Chọn tài khoản seed để xem email cấp tài khoản và khôi phục mật khẩu. Email của tài khoản thật được gửi đến hộp thư cá nhân.</p></div><Link href="/login" className="text-sm font-medium text-primary underline">Về đăng nhập</Link></header>
    {configError ? <AsyncState kind="error" message={configError} onRetry={() => { setConfigError(null); setAttempt(v => v + 1); }} /> : enabled === null ? <AsyncState kind="loading" message="Đang kiểm tra hộp thư demo…" /> : !enabled ? <AsyncState kind="empty" message="Hộp thư demo chưa được bật trong môi trường này." /> : <MailboxWorkspace />}
  </main>;
}

function MailboxWorkspace() {
  const [role, setRole] = useState("");
  const [search, setSearch] = useState("");
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(0);
  const [accounts, setAccounts] = useState<PageResult<MailboxAccount> | null>(null);
  const [selected, setSelected] = useState<MailboxAccount | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let disposed = false;
    accountSecurityApi.demoAccounts(role, keyword, page).then(value => { if (!disposed) setAccounts(value); }).catch(cause => { if (!disposed) setError(cause instanceof Error ? cause.message : "Không tải được tài khoản."); });
    return () => { disposed = true; };
  }, [role, keyword, page, attempt]);
  function changeFilter(nextRole: string, nextKeyword: string) { setRole(nextRole); setKeyword(nextKeyword); setPage(0); setAccounts(null); setSelected(null); setError(null); }
  function submit(event: FormEvent) { event.preventDefault(); changeFilter(role, search.trim()); setAttempt(v => v + 1); }
  return <div className="grid gap-5 lg:grid-cols-[20rem_minmax(0,1fr)]">
    <aside className="min-w-0 rounded-2xl border border-border bg-surface p-5">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Vai trò" htmlFor="mailbox-role"><select id="mailbox-role" className={controlClassName} value={role} onChange={e => changeFilter(e.target.value, keyword)}><option value="">Tất cả vai trò</option>{Object.entries(accountRoleLabels).filter(([value]) => value !== "SYSTEM").map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
        <Field label="Tìm tài khoản" htmlFor="mailbox-search"><input id="mailbox-search" className={controlClassName} value={search} onChange={e => setSearch(e.target.value)} placeholder="Tên hoặc email" maxLength={254} /></Field>
        <Button type="submit">Tìm kiếm</Button>
      </form>
      <div className="mt-5 space-y-2" aria-label="Tài khoản seed">
        {error ? <AsyncState kind="error" message={error} onRetry={() => { setError(null); setAttempt(v => v + 1); }} /> : !accounts ? <AsyncState kind="loading" message="Đang tìm tài khoản…" /> : !accounts.content.length ? <AsyncState kind="empty" message="Không có tài khoản seed phù hợp." /> : accounts.content.map(account => <button key={account.accountId} type="button" aria-pressed={selected?.accountId === account.accountId} onClick={() => setSelected(account)} className={`w-full rounded-xl border p-3 text-left ${selected?.accountId === account.accountId ? "border-primary bg-primary/5" : "border-border hover:bg-surface-muted"}`}><span className="block text-sm font-semibold">{account.displayName}</span><span className="mt-1 block break-all text-xs text-muted-foreground">{account.email}</span><span className="mt-2 block text-xs text-primary">{accountRoleLabels[account.role]}</span></button>)}
      </div>
      {accounts && accounts.totalPages > 1 ? <div className="mt-4 flex items-center justify-between gap-2 text-sm"><Button type="button" disabled={page === 0} onClick={() => { setPage(v => v - 1); setAccounts(null); }}>Trước</Button><span>{page + 1}/{accounts.totalPages}</span><Button type="button" disabled={page + 1 >= accounts.totalPages} onClick={() => { setPage(v => v + 1); setAccounts(null); }}>Sau</Button></div> : null}
    </aside>
    {selected ? <AccountInbox key={selected.accountId} account={selected} /> : <section className="grid min-h-96 place-items-center rounded-2xl border border-dashed border-border bg-surface p-8 text-center text-muted-foreground"><div><p className="text-lg font-medium text-foreground">Chọn một tài khoản để mở hộp thư</p><p className="mt-2 text-sm">Thư xuất hiện sau khi cấp tài khoản, reset hoặc yêu cầu quên mật khẩu.</p></div></section>}
  </div>;
}

function AccountInbox({ account }: { account: MailboxAccount }) {
  const [messages, setMessages] = useState<PageResult<MailboxMessage> | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [page, setPage] = useState(0);
  useEffect(() => {
    let disposed = false;
    accountSecurityApi.demoMessages(account.accountId, page).then(value => { if (!disposed) setMessages(value); }).catch(cause => { if (!disposed) setError(cause instanceof Error ? cause.message : "Không tải được thư."); });
    return () => { disposed = true; };
  }, [account.accountId, attempt, page]);
  function refresh() { setError(null); setMessages(null); setAttempt(v => v + 1); }
  return <section className="min-w-0 space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-surface p-5"><div><h2 className="text-lg font-semibold">{account.displayName}</h2><p className="mt-1 break-all text-sm text-muted-foreground">{account.email}</p></div><Button type="button" onClick={refresh}>Làm mới hộp thư</Button></div>
    <div className="overflow-hidden rounded-2xl border border-border bg-surface">
      {error ? <div className="p-5"><AsyncState kind="error" message={error} onRetry={refresh} /></div> : !messages ? <div className="p-5"><AsyncState kind="loading" message="Đang tải thư…" /></div> : !messages.content.length ? <div className="p-8"><AsyncState kind="empty" message="Hộp thư đang trống. Hãy yêu cầu đặt lại mật khẩu rồi làm mới." /></div> : messages.content.map(message => <button key={message.messageId} type="button" aria-pressed={selected === message.messageId} onClick={() => setSelected(message.messageId)} className={`block w-full border-b border-border p-4 text-left last:border-0 ${selected === message.messageId ? "bg-primary/5" : "hover:bg-surface-muted"}`}><span className="flex flex-wrap justify-between gap-2"><span className="text-sm font-semibold">{message.subject}</span><span className="text-xs text-muted-foreground">{formatInstant(message.createdAt)}</span></span><span className="mt-2 block truncate text-sm text-muted-foreground">{message.preview}</span></button>)}
    </div>
    {messages && messages.totalPages > 1 ? <div className="flex justify-between"><Button disabled={page === 0} onClick={() => { setMessages(null); setPage(v => v - 1); }}>Thư mới hơn</Button><Button disabled={page + 1 >= messages.totalPages} onClick={() => { setMessages(null); setPage(v => v + 1); }}>Thư cũ hơn</Button></div> : null}
    {selected ? <MessageView key={`${account.accountId}:${selected}`} accountId={account.accountId} messageId={selected} /> : null}
  </section>;
}

function MessageView({ accountId, messageId }: { accountId: string; messageId: string }) {
  const [message, setMessage] = useState<MailboxDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [plain, setPlain] = useState(false);
  useEffect(() => {
    let disposed = false;
    accountSecurityApi.demoMessage(accountId, messageId).then(value => { if (!disposed) setMessage(value); }).catch(cause => { if (!disposed) setError(cause instanceof Error ? cause.message : "Không đọc được thư."); });
    return () => { disposed = true; };
  }, [accountId, messageId, attempt]);
  const html = useMemo(() => message ? safeMailHtml(message.htmlBody) : "", [message]);
  if (error) return <AsyncState kind="error" message={error} onRetry={() => { setError(null); setAttempt(v => v + 1); }} />;
  if (!message) return <AsyncState kind="loading" message="Đang mở thư…" />;
  return <article className="overflow-hidden rounded-2xl border border-border bg-surface">
    <header className="space-y-3 border-b border-border p-5"><h3 className="text-xl font-semibold">{message.subject}</h3><dl className="space-y-1 break-all text-sm text-muted-foreground"><div><dt className="inline">Từ: </dt><dd className="inline">{message.from}</dd></div><div><dt className="inline">Đến: </dt><dd className="inline">{message.to}</dd></div><div><dt className="inline">Ngày: </dt><dd className="inline">{formatInstant(message.createdAt)}</dd></div></dl><button type="button" onClick={() => setPlain(v => !v)} className="text-sm font-medium text-primary underline">{plain ? "Xem email đầy đủ" : "Xem bản văn bản"}</button></header>
    {plain ? <pre className="whitespace-pre-wrap break-words p-6 font-sans text-sm leading-7">{message.textBody}</pre> : <iframe title={`Email: ${message.subject}`} sandbox="allow-top-navigation-by-user-activation" referrerPolicy="no-referrer" srcDoc={html} className="h-[46rem] w-full border-0 bg-white" />}
  </article>;
}
