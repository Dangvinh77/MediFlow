"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useState, useSyncExternalStore } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { DirectorySection } from "./RecordLayout";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import { departmentCategories, departmentCategoryHints, departmentCategoryLabels, roomErrorMessages, roomTypeRule } from "../rooms";
import type { DepartmentType, RoomTypeDTO } from "../types";

const getServerRole = () => null;

interface TypeDraft { code: string | null; departmentType: DepartmentType; name: string; description: string; singlePerDepartment: boolean }

function messageFrom(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return (cause.code && roomErrorMessages[cause.code]) || cause.message;
  return cause instanceof Error ? cause.message : fallback;
}

/**
 * "Danh mục khoa phòng": the three fixed department categories and the room types each one may
 * use. The options shown when creating a department room come from this catalogue. Admins add,
 * rename and delete types; the backend refuses to delete system or in-use types.
 */
export function RoomTypeCatalog() {
  const { localize } = useLocale();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const admin = role === "ADMIN";
  const [types, setTypes] = useState<RoomTypeDTO[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState<TypeDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const [draftError, setDraftError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const session = getSessionKey();
    organizationApi.roomTypes().then(rows => {
      if (active && isRequestSessionCurrent(session)) setTypes(rows);
    }).catch((cause: unknown) => {
      if (active && isRequestSessionCurrent(session, cause)) setError(messageFrom(cause, "Không thể tải danh mục loại phòng."));
    });
    return () => { active = false; };
  }, [attempt]);

  function startAdd(departmentType: DepartmentType) {
    setNotice(null); setActionError(null); setDraftError(null);
    // One room per department unless the admin explicitly allows several (clinical only).
    setDraft({ code: null, departmentType, name: "", description: "", singlePerDepartment: true });
  }

  function startRename(type: RoomTypeDTO) {
    setNotice(null); setActionError(null); setDraftError(null);
    setDraft({ code: type.code, departmentType: type.departmentType, name: type.name, description: type.description ?? "", singlePerDepartment: type.singlePerDepartment });
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft || saving) return;
    const name = draft.name.trim();
    if (name.length < 2) { setDraftError("Tên loại phòng cần từ 2 đến 100 ký tự."); return; }
    const description = draft.description.trim() || null;
    setSaving(true); setDraftError(null);
    try {
      const saved = draft.code
        ? await organizationApi.updateRoomType(draft.code, { name, description })
        : await organizationApi.createRoomType({ name, description, departmentType: draft.departmentType, singlePerDepartment: draft.singlePerDepartment });
      setTypes(current => {
        const rows = current ?? [];
        return rows.some(row => row.code === saved.code) ? rows.map(row => row.code === saved.code ? saved : row) : [...rows, saved];
      });
      setNotice(draft.code ? `Đã đổi tên loại phòng thành “${saved.name}”.` : `Đã thêm loại phòng “${saved.name}” vào ${departmentCategoryLabels[saved.departmentType].toLowerCase()}.`);
      setDraft(null);
    } catch (cause) {
      setDraftError(messageFrom(cause, "Không thể lưu loại phòng."));
    } finally { setSaving(false); }
  }

  async function remove(type: RoomTypeDTO) {
    setNotice(null); setActionError(null);
    if (!window.confirm(`Xóa loại phòng “${type.name}” khỏi danh mục? Loại phòng này sẽ không còn xuất hiện khi tạo phòng làm việc.`)) return;
    try {
      await organizationApi.deleteRoomType(type.code);
      setTypes(current => current?.filter(row => row.code !== type.code) ?? null);
      setNotice(`Đã xóa loại phòng “${type.name}”.`);
    } catch (cause) {
      setActionError(messageFrom(cause, "Không thể xóa loại phòng."));
    }
  }

  const form = (category: DepartmentType) => draft && draft.departmentType === category
    ? <form aria-label={localize(draft.code ? "Đổi tên loại phòng" : `Thêm loại phòng cho ${departmentCategoryLabels[category]}`)} onSubmit={save} noValidate className="grid gap-4 border-t border-border p-4 md:grid-cols-2">
      <Field label={localize("Tên loại phòng")} htmlFor={`room-type-name-${category}`} required><input id={`room-type-name-${category}`} maxLength={100} value={draft.name} disabled={saving} onChange={event => { const name = event.target.value; setDraft(current => current && ({ ...current, name })); }} placeholder={localize(category === "CLINICAL" ? "Ví dụ: Phòng thủ thuật" : "Ví dụ: Phòng siêu âm")} className={controlClassName} /></Field>
      <Field label={localize("Mô tả")} htmlFor={`room-type-description-${category}`}><input id={`room-type-description-${category}`} maxLength={255} value={draft.description} disabled={saving} onChange={event => { const description = event.target.value; setDraft(current => current && ({ ...current, description })); }} placeholder={localize("Công dụng của phòng")} className={controlClassName} /></Field>
      <L10nValue>{category === "CLINICAL" && !draft.code ? <label className="flex items-center gap-2 text-sm md:col-span-2"><input type="checkbox" checked={!draft.singlePerDepartment} disabled={saving} onChange={event => { const many = event.target.checked; setDraft(current => current && ({ ...current, singlePerDepartment: !many })); }} className="size-5 accent-primary" /><L10n>{"Cho phép một khoa có nhiều phòng loại này"}</L10n></label>
        : !draft.code ? <p className="text-xs text-muted-foreground md:col-span-2"><L10n>{"Khoa/bộ phận thuộc khối này làm việc tại đúng một phòng."}</L10n></p> : null}</L10nValue>
      <L10nValue>{!draft.code ? <p className="text-xs text-muted-foreground md:col-span-2"><L10n>{"Chỉ loại “Phòng khám” được dùng để lập buổi khám. Khối và quy tắc số phòng không đổi được sau khi tạo."}</L10n></p> : null}</L10nValue>
      <L10nValue>{draftError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger md:col-span-2"><L10nValue>{draftError}</L10nValue></p> : null}</L10nValue>
      <div className="flex flex-wrap gap-2 md:col-span-2"><Button type="submit" variant="primary" loading={saving}><L10nValue>{draft.code ? "Lưu tên" : "Thêm loại phòng"}</L10nValue></Button><Button type="button" disabled={saving} onClick={() => { setDraft(null); setDraftError(null); }}><L10n>{"Hủy"}</L10n></Button></div>
    </form>
    : null;

  return <div className="space-y-5">
    <L10nValue>{notice ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-3 text-sm text-success"><L10nValue>{notice}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{actionError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger"><L10nValue>{actionError}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{error ? <AsyncState kind="error" message={localize(error)} onRetry={() => { setError(null); setTypes(null); setAttempt(value => value + 1); }} />
      : !types ? <AsyncState kind="loading" message={localize("Đang tải danh mục khoa phòng…")} />
      : departmentCategories.map(category => {
        const rows = types.filter(type => type.departmentType === category);
        return <DirectorySection key={category} title={localize(departmentCategoryLabels[category])} description={localize(departmentCategoryHints[category])}
          actions={admin && !draft ? <Button variant="primary" onClick={() => startAdd(category)} aria-label={localize(`Thêm loại phòng cho ${departmentCategoryLabels[category]}`)}><L10n>{"Thêm loại phòng"}</L10n></Button> : undefined}>
          <L10nValue>{form(category)}</L10nValue>
          <L10nValue>{rows.length === 0 ? <AsyncState kind="empty" message={localize("Khối này chưa có loại phòng nào; khoa/bộ phận thuộc khối sẽ không tạo được phòng làm việc.")} />
            : <div className="overflow-x-auto border-t border-border"><table className="w-full min-w-2xl text-left text-sm">
              <caption className="sr-only"><L10n>{"Loại phòng của "}</L10n><L10nValue>{departmentCategoryLabels[category]}</L10nValue></caption>
              <thead className="border-b border-border text-muted-foreground"><tr><L10nValue>{["Loại phòng", "Quy tắc", "Số phòng đang dùng", ...(admin ? ["Thao tác"] : [])].map(label => <th key={label} scope="col" className="px-4 py-3"><L10nValue>{label}</L10nValue></th>)}</L10nValue></tr></thead>
              <tbody><L10nValue>{rows.map(type => {
                const locked = type.systemDefined ? "Loại phòng hệ thống" : type.roomCount > 0 ? "Đang được sử dụng" : null;
                return <tr key={type.code} className="border-b border-border last:border-0">
                  <td className="px-4 py-2"><span className="font-semibold">{type.name}</span><L10nValue>{type.schedulable ? <> <StatusBadge tone="info"><L10n>{"Dùng lập buổi khám"}</L10n></StatusBadge></> : null}</L10nValue><L10nValue>{type.description ? <span className="block text-xs text-muted-foreground"><L10nValue>{type.description}</L10nValue></span> : null}</L10nValue></td>
                  <td className="px-4 py-2"><L10nValue>{roomTypeRule(type)}</L10nValue></td>
                  <td className="px-4 py-2 tabular-nums">{type.roomCount}</td>
                  <L10nValue>{admin ? <td className="px-4 py-2"><div className="flex flex-nowrap items-center gap-2">
                    <Button type="button" disabled={Boolean(draft)} onClick={() => startRename(type)} aria-label={localize(`Đổi tên ${type.name}`)}><L10n>{"Đổi tên"}</L10n></Button>
                    <Button type="button" variant="danger" disabled={Boolean(locked) || Boolean(draft)} title={localize(locked ?? undefined)} onClick={() => void remove(type)} aria-label={localize(`Xóa ${type.name}`)}><L10n>{"Xóa"}</L10n></Button>
                    <L10nValue>{locked ? <span className="text-xs text-muted-foreground"><L10nValue>{locked}</L10nValue></span> : null}</L10nValue>
                  </div></td> : null}</L10nValue>
                </tr>;
              })}</L10nValue></tbody>
            </table></div>}</L10nValue>
        </DirectorySection>;
      })}</L10nValue>
  </div>;
}
