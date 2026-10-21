"use client";

import { useEffect, useRef, useState, type ReactNode, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { isRequestSessionCurrent } from "../request-session";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { isUuid } from "@/lib/validation";
import { getSessionKey } from "@/lib/auth";
import { organizationApi } from "../api";
import { StaffPicker } from "./StaffPicker";
import { AccountDirectory } from "./AccountDirectory";
import { PasswordForm } from "./PasswordForm";
import { CreationIntentError, prepareCreation } from "../creation-intent";
import type { AccountDTO, AccountRole } from "../types";

const roleLabels: Record<AccountRole, string> = { ADMIN: "Quản trị viên", DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", LAB_TECH: "Kỹ thuật viên xét nghiệm", MANAGER: "Quản lý", PATIENT: "Bệnh nhân", SYSTEM: "Hệ thống" };
const roles: AccountRole[] = ["ADMIN", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "MANAGER", "PATIENT"];

export function AccountAdmin({ renderPatientPicker }: { renderPatientPicker?: (value: string, onChange: (id: string) => void, disabled: boolean) => ReactNode }) {
  const router = useRouter();
  const [selected, setSelected] = useState<AccountDTO | null>(null);
  const [revision, setRevision] = useState(0);
  const mounted = useRef(true);
  const submitting = useRef(false);
  const [username, setUsername] = useState(""); const [password, setPassword] = useState(""); const [role, setRole] = useState<AccountRole>("DOCTOR"); const [ownerId, setOwnerId] = useState("");
  const [accountId, setAccountId] = useState(""); const [active, setActive] = useState(true); const [result, setResult] = useState<AccountDTO | null>(null); const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const id = ownerId.trim(); if (!isUuid(id)) { setError(`Mã ${role === "PATIENT" ? "bệnh nhân" : "nhân viên"} phải là UUID hợp lệ.`); return; }
    const sessionToken = getSessionKey();
    submitting.current = true;
    setBusy(true); setError(null); setResult(null);
    let intent: Awaited<ReturnType<typeof prepareCreation>> | undefined;
    try {
      const body = { username: username.trim(), password, role, staffId: role === "PATIENT" ? null : id, patientId: role === "PATIENT" ? id : null };
      // The server checks the password against a salted hash on replay. Never
      // persist the password or a fast password digest in browser storage.
      intent = await prepareCreation("account", sessionToken, { username: body.username, role, staffId: body.staffId, patientId: body.patientId });
      if (!mounted.current || getSessionKey() !== sessionToken) { intent.cancelUnsent(); return; }
      const account = await organizationApi.createAccount(body, intent.key);
      intent.settle();
      if (!mounted.current || getSessionKey() !== sessionToken) return;
      setResult(account);
      setRevision(value => value + 1);
      setPassword("");
    } catch (cause: unknown) {
      intent?.settle(cause);
      if (!mounted.current || !isRequestSessionCurrent(sessionToken, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      if (cause instanceof CreationIntentError) setError(cause.message);
      else if (!(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500) {
        setError("Chưa xác nhận được kết quả tạo tài khoản. Hãy gửi lại cùng thông tin và mật khẩu để lấy lại tài khoản bằng khóa đối soát cũ.");
      } else setError(cause instanceof Error ? cause.message : "Không thể tạo tài khoản.");
    } finally { submitting.current = false; if (mounted.current && getSessionKey() === sessionToken) setBusy(false); }
  }

  async function updateStatus(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const id = accountId.trim(); if (!isUuid(id)) { setError("Mã tài khoản phải là UUID hợp lệ."); return; }
    const sessionToken = getSessionKey();
    submitting.current = true;
    setBusy(true); setError(null); setResult(null);
    try {
      const account = await organizationApi.updateAccountStatus(id, active);
      if (!mounted.current || getSessionKey() !== sessionToken) return;
      setResult(account);
      setRevision(value => value + 1);
    } catch (cause: unknown) {
      if (!mounted.current || !isRequestSessionCurrent(sessionToken, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      setError(cause instanceof Error ? cause.message : "Không thể cập nhật tài khoản.");
    } finally { submitting.current = false; if (mounted.current && getSessionKey() === sessionToken) setBusy(false); }
  }

  return <section className="mt-6 space-y-6"><AccountDirectory revision={revision} onSelect={account => { setSelected(account); setAccountId(account.accountId); setActive(account.isActive); setResult(null); }} />{selected && selected.role !== "SYSTEM" ? <PasswordForm accountId={selected.accountId} username={selected.username} /> : null}{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}{result ? <div role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success"><p className="font-semibold">Đã cập nhật tài khoản {result.username}</p><p className="mt-2 text-xs">Mã tài khoản</p><p className="mt-1 break-all font-mono text-xs select-all">{result.accountId}</p><p className="mt-1">{roleLabels[result.role]} · {result.isActive ? "Đang hoạt động" : "Đã khóa"}</p></div> : null}<div className="grid items-start gap-6 xl:grid-cols-2"><form onSubmit={create} className="space-y-4 rounded-xl border border-border bg-surface p-6"><fieldset disabled={busy} className="space-y-4"><div><h2 className="text-lg font-semibold">Tạo tài khoản</h2><p className="mt-1 text-sm text-muted-foreground">Liên kết tài khoản với hồ sơ nhân sự hoặc bệnh nhân đã có.</p></div><Field label="Tên đăng nhập" htmlFor="account-username" required hint="3–50 ký tự: chữ, số, dấu chấm, gạch dưới hoặc gạch ngang."><input id="account-username" aria-describedby="account-username-hint" autoComplete="username" required minLength={3} maxLength={50} pattern="[a-zA-Z0-9._-]+" value={username} onChange={(event) => setUsername(event.target.value)} className={controlClassName} /></Field><Field label="Mật khẩu" htmlFor="account-password" required hint="Từ 8 đến 72 ký tự."><input id="account-password" aria-describedby="account-password-hint" autoComplete="new-password" required type="password" minLength={8} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} className={controlClassName} /></Field><Field label="Vai trò" htmlFor="account-role" required><select id="account-role" value={role} onChange={(event) => { setRole(event.target.value as AccountRole); setOwnerId(""); }} className={controlClassName}>{roles.map((value) => <option key={value} value={value}>{roleLabels[value]}</option>)}</select></Field>{role === "PATIENT" && renderPatientPicker ? renderPatientPicker(ownerId, setOwnerId, busy) : role !== "PATIENT" ? <StaffPicker id="account-owner" label="Nhân viên" value={ownerId} onChange={setOwnerId} disabled={busy} required /> : <Field label="Mã bệnh nhân" htmlFor="account-owner" required><input id="account-owner" required value={ownerId} onChange={event => setOwnerId(event.target.value)} className={controlClassName} /></Field>}<Button type="submit" className="w-full sm:w-auto" variant="primary" loading={busy}>Tạo tài khoản</Button></fieldset></form><form onSubmit={updateStatus} className="space-y-4 rounded-xl border border-border bg-surface p-6"><fieldset disabled={busy} className="space-y-4"><div><h2 className="text-lg font-semibold">Khóa hoặc mở tài khoản</h2><p className="mt-1 text-sm text-muted-foreground">Nhập mã tài khoản cần quản lý và chọn trạng thái truy cập.</p></div><Field label="Mã tài khoản" htmlFor="account-id" required><input id="account-id" required value={accountId} onChange={(event) => setAccountId(event.target.value)} className={controlClassName} /></Field><Field label="Trạng thái" htmlFor="account-active"><select id="account-active" value={String(active)} onChange={(event) => setActive(event.target.value === "true")} className={controlClassName}><option value="true">Đang hoạt động</option><option value="false">Khóa tài khoản</option></select></Field><Button type="submit" className="w-full sm:w-auto" variant="primary" loading={busy}>Cập nhật trạng thái</Button></fieldset></form></div></section>;
}
