"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { isUuid } from "@/lib/validation";
import { organizationApi } from "../api";
import type { AccountDTO, AccountRole } from "../types";

const roles: AccountRole[] = ["ADMIN", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "MANAGER", "PATIENT"];

export function AccountAdmin() {
  const [username, setUsername] = useState(""); const [password, setPassword] = useState(""); const [role, setRole] = useState<AccountRole>("DOCTOR"); const [ownerId, setOwnerId] = useState("");
  const [accountId, setAccountId] = useState(""); const [active, setActive] = useState(true); const [result, setResult] = useState<AccountDTO | null>(null); const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null);

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const id = ownerId.trim(); if (!isUuid(id)) { setError(`Mã ${role === "PATIENT" ? "bệnh nhân" : "nhân viên"} phải là UUID hợp lệ.`); return; }
    setBusy(true); setError(null);
    try { setResult(await organizationApi.createAccount({ username: username.trim(), password, role, staffId: role === "PATIENT" ? null : id, patientId: role === "PATIENT" ? id : null })); setPassword(""); }
    catch (cause: unknown) { setError(cause instanceof Error ? cause.message : "Không thể tạo tài khoản."); }
    finally { setBusy(false); }
  }

  async function updateStatus(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const id = accountId.trim(); if (!isUuid(id)) { setError("Mã tài khoản phải là UUID hợp lệ."); return; }
    setBusy(true); setError(null);
    try { setResult(await organizationApi.updateAccountStatus(id, active)); }
    catch (cause: unknown) { setError(cause instanceof Error ? cause.message : "Không thể cập nhật tài khoản."); }
    finally { setBusy(false); }
  }

  return <section className="mt-6 space-y-6">{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}{result ? <div role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success"><p className="font-semibold">Đã cập nhật tài khoản {result.username}</p><p className="mt-1 break-all font-mono text-xs">{result.accountId}</p><p className="mt-1">{result.role} · {result.isActive ? "Đang hoạt động" : "Đã khóa"}</p></div> : null}<div className="grid gap-6 xl:grid-cols-2"><form onSubmit={create} className="space-y-4 rounded-xl border border-border bg-surface p-6"><div><h2 className="text-lg font-semibold">Tạo tài khoản</h2><p className="mt-1 text-sm text-muted-foreground">Tài khoản nhân viên gắn staffId; tài khoản PATIENT gắn patientId.</p></div><Field label="Tên đăng nhập" htmlFor="account-username" required><input id="account-username" required minLength={3} maxLength={50} pattern="[a-zA-Z0-9._-]+" value={username} onChange={(event) => setUsername(event.target.value)} className={controlClassName} /></Field><Field label="Mật khẩu" htmlFor="account-password" required><input id="account-password" required type="password" minLength={8} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} className={controlClassName} /></Field><Field label="Vai trò" htmlFor="account-role" required><select id="account-role" value={role} onChange={(event) => setRole(event.target.value as AccountRole)} className={controlClassName}>{roles.map((value) => <option key={value} value={value}>{value}</option>)}</select></Field><Field label={role === "PATIENT" ? "Mã bệnh nhân" : "Mã nhân viên"} htmlFor="account-owner" required><input id="account-owner" required value={ownerId} onChange={(event) => setOwnerId(event.target.value)} className={controlClassName} /></Field><Button type="submit" variant="primary" loading={busy}>Tạo tài khoản</Button></form><form onSubmit={updateStatus} className="space-y-4 rounded-xl border border-border bg-surface p-6"><div><h2 className="text-lg font-semibold">Khóa hoặc mở tài khoản</h2><p className="mt-1 text-sm text-muted-foreground">Dùng mã tài khoản trả về lúc tạo.</p></div><Field label="Mã tài khoản" htmlFor="account-id" required><input id="account-id" required value={accountId} onChange={(event) => setAccountId(event.target.value)} className={controlClassName} /></Field><Field label="Trạng thái" htmlFor="account-active"><select id="account-active" value={String(active)} onChange={(event) => setActive(event.target.value === "true")} className={controlClassName}><option value="true">Đang hoạt động</option><option value="false">Khóa tài khoản</option></select></Field><Button type="submit" variant="primary" loading={busy}>Cập nhật trạng thái</Button></form></div></section>;
}
