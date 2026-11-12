"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


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
export function AccountAdmin({ renderPatientPicker, loadPatientIdentity, initialOwnerId = "", initialOwnerType = "staff" }: { renderPatientPicker?: (value: string, onChange: (id: string) => void, disabled: boolean) => ReactNode; loadPatientIdentity?: (id: string) => Promise<string | null>; initialOwnerId?: string; initialOwnerType?: "staff" | "patient" }) {
  const { localize } = useLocale();
  const router = useRouter();
  const mounted = useRef(true);
  const submitting = useRef(false);
  const [username, setUsername] = useState("");
  const [ownerType, setOwnerType] = useState(initialOwnerType);
  const [ownerId, setOwnerId] = useState(initialOwnerId);
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
    if (!isUuid(ownerId)) return;
    let disposed = false;
    const session = getSessionKey();
    const load = ownerType === "staff"
      ? organizationApi.staffMember(ownerId).then(value => ({ username: value.email, staff: value }))
      : loadPatientIdentity ? loadPatientIdentity(ownerId).then(username => ({ username, staff: null }))
      : Promise.reject(new Error("Không thể tải email bệnh nhân."));
    load.then(({ username, staff: value }) => {
      if (!disposed && getSessionKey() === session) {
        if (value && !value.active) { setStaffError("Nhân viên đã ngừng làm việc. Hãy chọn nhân viên khác."); return; }
        if (!username?.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(username.trim())) {
          setStaffError("Hồ sơ chưa có email hợp lệ để đăng nhập và nhận mật khẩu. Hãy cập nhật hồ sơ trước."); return;
        }
        setUsername(username.trim());
        setStaff(value);
      }
    }).catch((cause: unknown) => {
      if (disposed || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setStaffError(cause instanceof Error ? cause.message : "Không tải được email của hồ sơ.");
    });
    return () => { disposed = true; };
  }, [ownerId, ownerType, staffAttempt, router, loadPatientIdentity]);

  function selectOwner(id: string) { setUsername(""); setOwnerId(id); setStaff(null); setStaffError(null); setError(null); }

  async function send(body: CreateAccountRequest) {
    if (submitting.current) return;
    const session = getSessionKey();
    submitting.current = true; setBusy(true); setError(null);
    let intent: Awaited<ReturnType<typeof prepareCreation>> | undefined;
    let replacementKey: string | undefined;
    try {
      // Persist command metadata so a lost response can reuse the same issuance key.
      const identity = { username: body.username, role: body.role, staffId: body.staffId, patientId: body.patientId };
      intent = await prepareCreation("account", session, identity);
      if (!mounted.current || getSessionKey() !== session) { intent.cancelUnsent(); return; }
      replacementKey = `organization:account-replacement:${intent.key}`;
      if (!body.replaceActiveAccount && sessionStorage.getItem(replacementKey) === "true") {
        setConfirmation({ body, message: "Yêu cầu trước đã xác nhận thay tài khoản active nhưng chưa nhận được kết quả. Tiếp tục để đối soát chính yêu cầu đó; không tạo thêm tài khoản." });
        return;
      }
      if (body.replaceActiveAccount) sessionStorage.setItem(replacementKey, "true");
      const account = body.patientId
        ? await organizationApi.provisionPatientAccount(body.patientId, intent.key)
        : await organizationApi.createAccount(body, intent.key);
      intent.settle();
      sessionStorage.removeItem(replacementKey);
      if (!mounted.current || getSessionKey() !== session) return;
      setConfirmation(null); setUnresolved(null);
      router.push(`/organization/accounts/${account.accountId}`);
    } catch (cause: unknown) {
      intent?.settle(cause);
      if (replacementKey && cause instanceof ApiRequestError && cause.status >= 400 && cause.status < 500 && cause.status !== 408 && !cause.code?.endsWith("IDEMPOTENCY_KEY_REUSED")) sessionStorage.removeItem(replacementKey);
      if (!mounted.current || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      if (body.staffId && cause instanceof ApiRequestError && cause.status === 409 && cause.code?.endsWith("ACCOUNT_OWNER_ACTIVE")) {
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
    if (locked || !role || !username || staffError) return;
    if (!isUuid(ownerId)) { setError("Vui lòng chọn hồ sơ liên kết hợp lệ."); return; }
    void send({ username: username.trim().toLowerCase(), role, staffId: ownerType === "staff" ? ownerId : null, patientId: ownerType === "patient" ? ownerId : null });
  }

  return <section className="mt-6 max-w-3xl space-y-5">
    <form onSubmit={create} className="rounded-xl border border-border bg-surface p-5 sm:p-6">
      <fieldset disabled={locked} className="space-y-5">
        <div><h2 className="text-lg font-semibold"><L10n>{"Thông tin tài khoản mới"}</L10n></h2><p className="mt-1 text-sm text-muted-foreground"><L10n>{"Chọn hồ sơ liên kết để tự động xác định vai trò truy cập."}</L10n></p></div>
        <Field label={localize("Email đăng nhập")} htmlFor="account-username" required hint="Lấy từ email của hồ sơ được chọn."><input id="account-username" type="email" required maxLength={254} autoComplete="off" readOnly value={username} className={controlClassName} /></Field>
        <p className="rounded-lg bg-primary/5 p-4 text-sm leading-6 text-muted-foreground"><L10n>{"Hệ thống sẽ gửi mật khẩu tạm và link đăng nhập đến email này. Người dùng phải đổi mật khẩu trong lần đăng nhập đầu tiên. Tài khoản seed nhận thư trong hộp thư demo."}</L10n></p>
        <Field label={localize("Loại hồ sơ liên kết")} htmlFor="account-owner-type"><select id="account-owner-type" className={controlClassName} value={ownerType} onChange={event => { setOwnerType(event.target.value === "patient" ? "patient" : "staff"); selectOwner(""); }}><option value="staff"><L10n>{"Nhân viên"}</L10n></option><option value="patient"><L10n>{"Bệnh nhân"}</L10n></option></select></Field>
        <L10nValue>{ownerType === "staff" ? <StaffPicker id="account-owner" label={localize("Nhân viên")} value={ownerId} onChange={selectOwner} disabled={locked} required /> : renderPatientPicker ? renderPatientPicker(ownerId, selectOwner, locked) : <Field label={localize("Mã bệnh nhân")} htmlFor="account-owner" required><input id="account-owner" required className={controlClassName} value={ownerId} onChange={event => selectOwner(event.target.value)} /></Field>}</L10nValue>
        <Field label={localize("Vai trò tự động")} htmlFor="account-role" hint="Vai trò được xác định từ chức danh của nhân viên hoặc hồ sơ bệnh nhân."><input id="account-role" readOnly className={controlClassName} value={role ? accountRoleLabels[role] : ownerId ? "Đang tải chức danh…" : "Chưa chọn hồ sơ"} /></Field>
        <L10nValue>{staffError ? <div role="alert" className="text-sm text-danger"><L10nValue>{staffError}</L10nValue> <Button type="button" onClick={() => { setStaffError(null); setStaffAttempt(value => value + 1); }}><L10n>{"Tải lại hồ sơ"}</L10n></Button></div> : null}</L10nValue>
        <Button type="submit" variant="primary" loading={busy} disabled={!role || !isUuid(ownerId) || !username || Boolean(staffError)}><L10n>{"Tạo tài khoản"}</L10n></Button>
      </fieldset>
    </form>
    <L10nValue>{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><L10nValue>{error}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{unresolved ? <Button type="button" loading={busy} onClick={() => void send(unresolved)}><L10n>{"Thử lại yêu cầu tạo"}</L10n></Button> : null}</L10nValue>
    <L10nValue>{confirmation ? <div role="alertdialog" aria-modal="false" aria-labelledby="account-confirm-title" aria-describedby="account-confirm-message" className="space-y-4 rounded-xl border border-warning/40 bg-surface p-5"><h2 id="account-confirm-title" className="font-semibold"><L10n>{"Xác nhận thay tài khoản đang active"}</L10n></h2><p id="account-confirm-message" className="text-sm"><L10nValue>{confirmation.message}</L10nValue></p><div className="flex flex-wrap gap-3"><Button type="button" disabled={busy} onClick={() => setConfirmation(null)}><L10n>{"Hủy"}</L10n></Button><Button type="button" variant="primary" loading={busy} onClick={() => void send({ ...confirmation.body, replaceActiveAccount: true })}><L10n>{"Tiếp tục tạo tài khoản"}</L10n></Button></div></div> : null}</L10nValue>
  </section>;
}
