"use client";

import { useEffect, useRef, useState, type ReactNode, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/auth";
import { isUuid } from "@/lib/validation";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { organizationApi } from "../api";
import { accountRoleLabels, roleForJobTitle } from "../account-roles";
import { isRequestSessionCurrent } from "../request-session";
import { CreationIntentError, prepareCreation } from "../creation-intent";
import type { CreateAccountRequest, StaffDTO } from "../types";
import { StaffPicker } from "./StaffPicker";

/** Account creation only; account management lives on the detail route. */
export function AccountAdmin({ renderPatientPicker }: { renderPatientPicker?: (value: string, onChange: (id: string) => void, disabled: boolean) => ReactNode }) {
  const router = useRouter();
  const mounted = useRef(true);
  const submitting = useRef(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [ownerType, setOwnerType] = useState("staff");
  const [ownerId, setOwnerId] = useState("");
  const [staff, setStaff] = useState<StaffDTO | null>(null);
  const [staffError, setStaffError] = useState<string | null>(null);
  const [staffAttempt, setStaffAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState<{ body: CreateAccountRequest; message: string } | null>(null);
  const [unresolved, setUnresolved] = useState<CreateAccountRequest | null>(null);
  const role = ownerType === "patient" ? "PATIENT" : staff?.staffId === ownerId && staff.active ? roleForJobTitle[staff.jobTitle] : null;
  const locked = busy || confirmation !== null || unresolved !== null;

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    if (ownerType !== "staff" || !isUuid(ownerId)) return;
    let disposed = false;
    const session = getSessionKey();
    organizationApi.staffMember(ownerId).then(value => {
      if (!disposed && getSessionKey() === session) {
        if (!value.active) setStaffError("Nhân viên đã ngừng làm việc. Hãy chọn nhân viên khác.");
        else setStaff(value);
      }
    }).catch((cause: unknown) => {
      if (disposed || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setStaffError(cause instanceof Error ? cause.message : "Không tải được chức danh nhân viên.");
    });
    return () => { disposed = true; };
  }, [ownerId, ownerType, staffAttempt, router]);

  function selectOwner(id: string) { setOwnerId(id); setStaff(null); setStaffError(null); setError(null); }

  async function send(body: CreateAccountRequest) {
    if (submitting.current) return;
    const session = getSessionKey();
    submitting.current = true; setBusy(true); setError(null);
    let intent: Awaited<ReturnType<typeof prepareCreation>> | undefined;
    let replacementKey: string | undefined;
    try {
      // Store only command metadata. Passwords stay in this mounted form's memory.
      const identity = { username: body.username, role: body.role, staffId: body.staffId, patientId: body.patientId };
      intent = await prepareCreation("account", session, identity);
      if (!mounted.current || getSessionKey() !== session) { intent.cancelUnsent(); return; }
      replacementKey = `organization:account-replacement:${intent.key}`;
      if (!body.replaceActiveAccount && sessionStorage.getItem(replacementKey) === "true") {
        setConfirmation({ body, message: "Yêu cầu trước đã xác nhận thay tài khoản active nhưng chưa nhận được kết quả. Tiếp tục để đối soát chính yêu cầu đó; không tạo thêm tài khoản." });
        return;
      }
      if (body.replaceActiveAccount) sessionStorage.setItem(replacementKey, "true");
      const account = await organizationApi.createAccount(body, intent.key);
      intent.settle();
      sessionStorage.removeItem(replacementKey);
      if (!mounted.current || getSessionKey() !== session) return;
      setPassword(""); setConfirmation(null); setUnresolved(null);
      router.push(`/organization/accounts/${account.accountId}`);
    } catch (cause: unknown) {
      intent?.settle(cause);
      if (replacementKey && cause instanceof ApiRequestError && cause.status >= 400 && cause.status < 500 && cause.status !== 408 && !cause.code?.endsWith("IDEMPOTENCY_KEY_REUSED")) sessionStorage.removeItem(replacementKey);
      if (!mounted.current || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      if (cause instanceof ApiRequestError && cause.status === 409 && cause.code?.endsWith("ACCOUNT_OWNER_ACTIVE")) {
        setUnresolved(null); setConfirmation({ body, message: cause.message });
      } else if ((!(cause instanceof ApiRequestError) && !(cause instanceof CreationIntentError))
        || (cause instanceof ApiRequestError && (cause.status < 400 || cause.status === 408 || cause.status >= 500))) {
        setConfirmation(null); setUnresolved(body);
        setError("Chưa xác nhận được kết quả tạo. Thử lại cùng yêu cầu để đối soát bằng khóa cũ.");
      } else {
        setConfirmation(null); setUnresolved(null);
        setError(cause instanceof Error ? cause.message : "Không thể tạo tài khoản.");
      }
    } finally { submitting.current = false; if (mounted.current && getSessionKey() === session) setBusy(false); }
  }

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (locked || !role) return;
    if (!isUuid(ownerId)) { setError("Vui lòng chọn hồ sơ liên kết hợp lệ."); return; }
    if (new TextEncoder().encode(password).length > 72) { setError("Mật khẩu không được quá 72 byte UTF-8."); return; }
    void send({ username: username.trim(), password, role, staffId: ownerType === "staff" ? ownerId : null, patientId: ownerType === "patient" ? ownerId : null });
  }

  return <section className="mt-6 max-w-3xl space-y-5">
    <form onSubmit={create} className="rounded-xl border border-border bg-surface p-5 sm:p-6">
      <fieldset disabled={locked} className="space-y-5">
        <div><h2 className="text-lg font-semibold">Thông tin tài khoản mới</h2><p className="mt-1 text-sm text-muted-foreground">Chọn hồ sơ liên kết để tự động xác định vai trò truy cập.</p></div>
        <Field label="Tên đăng nhập" htmlFor="account-username" required hint="3–50 ký tự: chữ, số, dấu chấm, gạch dưới hoặc gạch ngang."><input id="account-username" required minLength={3} maxLength={50} pattern="[a-zA-Z0-9._-]+" autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} className={controlClassName} /></Field>
        <Field label="Mật khẩu" htmlFor="account-password" required hint="Từ 8 đến 72 ký tự."><input id="account-password" required type="password" minLength={8} maxLength={72} autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} className={controlClassName} /></Field>
        <Field label="Loại hồ sơ liên kết" htmlFor="account-owner-type"><select id="account-owner-type" className={controlClassName} value={ownerType} onChange={event => { setOwnerType(event.target.value); selectOwner(""); }}><option value="staff">Nhân viên</option><option value="patient">Bệnh nhân</option></select></Field>
        {ownerType === "staff" ? <StaffPicker id="account-owner" label="Nhân viên" value={ownerId} onChange={selectOwner} disabled={locked} required /> : renderPatientPicker ? renderPatientPicker(ownerId, selectOwner, locked) : <Field label="Mã bệnh nhân" htmlFor="account-owner" required><input id="account-owner" required className={controlClassName} value={ownerId} onChange={event => selectOwner(event.target.value)} /></Field>}
        <Field label="Vai trò tự động" htmlFor="account-role" hint="Vai trò được xác định từ chức danh của nhân viên hoặc hồ sơ bệnh nhân."><input id="account-role" readOnly className={controlClassName} value={role ? accountRoleLabels[role] : ownerId ? "Đang tải chức danh…" : "Chưa chọn hồ sơ"} /></Field>
        {staffError ? <div role="alert" className="text-sm text-danger">{staffError} <Button type="button" onClick={() => { setStaffError(null); setStaffAttempt(value => value + 1); }}>Tải lại chức danh</Button></div> : null}
        <Button type="submit" variant="primary" loading={busy} disabled={!role || !isUuid(ownerId)}>Tạo tài khoản</Button>
      </fieldset>
    </form>
    {error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}
    {unresolved ? <Button type="button" loading={busy} onClick={() => void send(unresolved)}>Thử lại yêu cầu tạo</Button> : null}
    {confirmation ? <div role="alertdialog" aria-modal="false" aria-labelledby="account-confirm-title" aria-describedby="account-confirm-message" className="space-y-4 rounded-xl border border-warning/40 bg-surface p-5"><h2 id="account-confirm-title" className="font-semibold">Xác nhận thay tài khoản đang active</h2><p id="account-confirm-message" className="text-sm">{confirmation.message}</p><div className="flex flex-wrap gap-3"><Button type="button" disabled={busy} onClick={() => setConfirmation(null)}>Hủy</Button><Button type="button" variant="primary" loading={busy} onClick={() => void send({ ...confirmation.body, replaceActiveAccount: true })}>Tiếp tục tạo tài khoản</Button></div></div> : null}
  </section>;
}
