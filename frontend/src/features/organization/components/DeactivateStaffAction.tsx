"use client";

import { useEffect, useId, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { L10n, useLocale } from "@/components/i18n/LocaleProvider";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import type { StaffDTO } from "../types";

interface Props { staff: StaffDTO; onChanged: (staff: StaffDTO) => void }
const serverSnapshot = () => null;

export function DeactivateStaffAction(props: Props) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, serverSnapshot);
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, serverSnapshot);
  if (role !== "ADMIN" || !props.staff.active) return null;
  return <DeactivateStaffWorkspace key={`${props.staff.staffId}:${session}`} {...props} />;
}

function DeactivateStaffWorkspace({ staff, onChanged }: Props) {
  const { localize } = useLocale();
  const router = useRouter();
  const titleId = useId(); const descriptionId = useId();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [unknown, setUnknown] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const lock = useRef(false);
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => { if (open) panel.current?.focus(); }, [open]);

  function cancel() {
    if (lock.current || unknown) return;
    setOpen(false); setError(null); trigger.current?.focus();
  }

  async function submit(readBack = false) {
    if (lock.current || !open || getRole() !== "ADMIN" || (unknown && !readBack)) return;
    lock.current = true; setBusy(true); setError(null);
    const session = getSessionKey();
    const current = (cause?: unknown) => alive.current && isRequestSessionCurrent(session, cause);
    try {
      const result = readBack ? await organizationApi.staffMember(staff.staffId) : await organizationApi.deactivateStaff(staff.staffId);
      if (!current()) return;
      if (result.staffId !== staff.staffId || result.active !== false) {
        setUnknown(true);
        setError("Chưa xác nhận được nhân viên đã ngừng làm việc. Hãy kiểm tra lại trạng thái trước khi tiếp tục.");
        return;
      }
      onChanged(result);
    } catch (cause) {
      if (!current(cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
      const uncertain = readBack || !(cause instanceof ApiRequestError) || cause.status === 408 || cause.status >= 500;
      if (uncertain) setUnknown(true);
      setError(uncertain
        ? "Chưa xác định được kết quả. Hãy kiểm tra trạng thái; hệ thống sẽ không tự gửi lại yêu cầu."
        : cause instanceof Error ? cause.message : "Không thể ngừng làm việc nhân viên.");
    } finally {
      if (current()) { lock.current = false; setBusy(false); }
    }
  }

  return <div className="space-y-3">
    <button type="button" ref={trigger} onClick={() => setOpen(true)} className="inline-flex min-h-11 items-center rounded-lg border border-danger/40 px-3 py-2 text-sm font-semibold text-danger hover:bg-danger/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"><L10n>Ngừng làm việc</L10n></button>
    {open ? <div role="alertdialog" tabIndex={-1} ref={panel} aria-labelledby={titleId} aria-describedby={descriptionId} onKeyDown={event => { if (event.key === "Escape") { event.preventDefault(); cancel(); } }} className="max-w-lg space-y-3 rounded-xl border border-danger/40 bg-surface p-4 whitespace-normal">
      <h3 id={titleId} className="font-semibold"><L10n>Ngừng làm việc nhân viên?</L10n></h3>
      <p className="font-medium">{staff.fullName}</p>
      <p id={descriptionId} className="text-sm text-muted-foreground"><L10n>Hồ sơ và lịch sử được giữ lại. Tài khoản liên kết sẽ bị khóa. Hãy bàn giao lịch làm việc và công việc đang xử lý trước khi xác nhận.</L10n></p>
      {error ? <p role="alert" className="text-sm text-danger">{localize(error)}</p> : null}
      <div className="flex flex-wrap gap-2">
        {unknown ? <Button type="button" loading={busy} onClick={() => void submit(true)}><L10n>Kiểm tra trạng thái</L10n></Button> : <>
          <Button type="button" variant="danger" loading={busy} disabled={busy} onClick={() => void submit()}><L10n>Xác nhận ngừng làm việc</L10n></Button>
          <Button type="button" disabled={busy} onClick={cancel}><L10n>Hủy</L10n></Button>
        </>}
      </div>
    </div> : null}
  </div>;
}
