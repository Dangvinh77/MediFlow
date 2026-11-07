"use client";

import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { DirectorySection } from "./RecordLayout";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
import { departmentCategories, departmentCategoryLabels, roomErrorMessages, roomTypeName, roomTypeRule, roomTypesFor } from "../rooms";
import type { DepartmentDTO, RoomDTO, RoomType, RoomTypeDTO } from "../types";

const getServerRole = () => null;

interface RoomForm {
  roomId: string | null;
  departmentId: string;
  roomType: RoomType | "";
  roomNumber: string;
  roomName: string;
  zone: string;
  location: string;
}

const emptyForm = (departmentId = ""): RoomForm => ({ roomId: null, departmentId, roomType: "", roomNumber: "", roomName: "", zone: "", location: "" });

function messageFrom(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return (cause.code && roomErrorMessages[cause.code]) || cause.message;
  return cause instanceof Error ? cause.message : fallback;
}

/** First filter step: which dimension the second select ("Đối tượng lọc") lists. */
type FilterDimension = "" | "department" | "type";

const normalize = (text: string) => text.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[đĐ]/g, "d").toLowerCase();

/**
 * Workplace rooms: consultation rooms planned for clinical departments, the symbolic office of
 * each department, and the single workplace of each paraclinical or administrative unit. Room
 * types come from the administrator-managed catalogue. Admins create and edit.
 */
export function RoomDirectory({ fixedDepartment }: { fixedDepartment?: DepartmentDTO }) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const admin = role === "ADMIN";
  const [departments, setDepartments] = useState<DepartmentDTO[]>(fixedDepartment ? [fixedDepartment] : []);
  const [rooms, setRooms] = useState<RoomDTO[] | null>(null);
  const [types, setTypes] = useState<RoomTypeDTO[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [dimension, setDimension] = useState<FilterDimension>(fixedDepartment ? "type" : "");
  const [target, setTarget] = useState("");
  const [keyword, setKeyword] = useState("");
  const [form, setForm] = useState<RoomForm | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const fixedDepartmentId = fixedDepartment?.departmentId;
  useEffect(() => {
    let active = true;
    const session = getSessionKey();
    Promise.all([
      organizationApi.rooms({ departmentId: fixedDepartmentId }),
      fixedDepartmentId ? Promise.resolve(null) : organizationApi.departments(false),
      organizationApi.roomTypes(),
    ]).then(([roomRows, departmentRows, typeRows]) => {
      if (!active || !isRequestSessionCurrent(session)) return;
      setRooms(roomRows); setTypes(typeRows); if (departmentRows) setDepartments(departmentRows);
    }).catch((cause: unknown) => {
      if (active && isRequestSessionCurrent(session, cause)) setError(messageFrom(cause, "Không thể tải danh sách phòng làm việc."));
    });
    return () => { active = false; };
  }, [attempt, fixedDepartmentId]);

  const departmentById = useMemo(() => new Map(departments.map(department => [department.departmentId, department])), [departments]);
  const departmentFilter = fixedDepartment?.departmentId ?? (dimension === "department" ? target : "");
  const typeFilter = dimension === "type" ? target : "";
  const filterTypes = fixedDepartment ? roomTypesFor(types, fixedDepartment.departmentType) : types;
  const visibleRooms = (rooms ?? []).filter(room => (!departmentFilter || room.departmentId === departmentFilter)
    && (!typeFilter || room.roomType === typeFilter)
    && normalize(`${room.roomNumber} ${room.roomName} ${room.zone} ${room.location}`).includes(normalize(keyword.trim())));
  const formDepartment = form ? departmentById.get(form.departmentId) : undefined;
  const allowedTypes = roomTypesFor(types, formDepartment?.departmentType);
  const selectedType = form ? types.find(type => type.code === form.roomType) : undefined;

  function startCreate() {
    setNotice(null); setFormError(null);
    setForm(emptyForm(fixedDepartment?.departmentId ?? departmentFilter));
  }

  function startEdit(room: RoomDTO) {
    setNotice(null); setFormError(null);
    setForm({ roomId: room.roomId, departmentId: room.departmentId, roomType: room.roomType, roomNumber: room.roomNumber, roomName: room.roomName, zone: room.zone, location: room.location });
  }

  function chooseDepartment(departmentId: string) {
    const department = departmentById.get(departmentId);
    const choices = roomTypesFor(types, department?.departmentType).map(type => type.code);
    setForm(current => current && ({
      ...current, departmentId,
      // Preselect the only fitting type, if the category has exactly one.
      roomType: current.roomType && choices.includes(current.roomType) ? current.roomType : choices.length === 1 ? choices[0] : "",
      location: current.location || department?.location || "",
    }));
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form || saving) return;
    if (!form.departmentId || !form.roomType || !form.roomNumber.trim() || !form.roomName.trim() || !form.zone.trim() || !form.location.trim()) {
      setFormError("Nhập đủ khoa/bộ phận, loại phòng, số phòng, tên phòng, khu và vị trí.");
      return;
    }
    const placement = { roomNumber: form.roomNumber.trim(), roomName: form.roomName.trim(), zone: form.zone.trim(), location: form.location.trim() };
    setSaving(true); setFormError(null);
    try {
      const saved = form.roomId
        ? await organizationApi.updateRoom(form.roomId, placement)
        : await organizationApi.createRoom({ ...placement, departmentId: form.departmentId, roomType: form.roomType });
      setRooms(current => current ? [saved, ...current.filter(room => room.roomId !== saved.roomId)] : [saved]);
      setNotice(form.roomId ? "Đã cập nhật phòng làm việc." : "Đã thêm phòng làm việc.");
      setForm(null);
    } catch (cause) {
      setFormError(messageFrom(cause, "Không thể lưu phòng làm việc."));
    } finally { setSaving(false); }
  }

  async function toggle(room: RoomDTO) {
    setNotice(null); setError(null);
    try {
      const saved = await organizationApi.changeRoomStatus(room.roomId, !room.active);
      setRooms(current => current?.map(item => item.roomId === saved.roomId ? saved : item) ?? null);
      setNotice(saved.active ? "Đã mở lại phòng." : "Đã ngừng sử dụng phòng.");
    } catch (cause) {
      setNotice(null); setFormError(messageFrom(cause, "Không thể đổi trạng thái phòng."));
    }
  }

  const title = fixedDepartment ? "Phòng làm việc của đơn vị" : "Phòng làm việc";
  const description = fixedDepartment
    ? fixedDepartment.departmentType === "CLINICAL"
      ? "Các phòng khám được hoạch định cho khoa và văn phòng chung của khoa."
      : "Bộ phận chức năng làm việc chung tại một phòng; mọi nhân sự đều làm việc tại đây."
    : "Phòng khám phân bổ cho khoa, văn phòng khoa và nơi làm việc của các khoa cận lâm sàng và phòng chức năng. Loại phòng được quản lý tại Danh mục khoa phòng.";

  return <DirectorySection title={title} description={description} actions={admin && !form ? <Button variant="primary" onClick={startCreate}>Thêm phòng làm việc</Button> : undefined}>
    {notice ? <p role="status" className="mx-5 mb-4 rounded-lg border border-success/40 bg-success/10 p-3 text-sm text-success">{notice}</p> : null}
    {form ? <form aria-label={form.roomId ? "Sửa phòng làm việc" : "Thêm phòng làm việc"} onSubmit={save} noValidate className="grid gap-4 border-t border-border p-5 md:grid-cols-2 xl:grid-cols-3">
      <Field label="Khoa / bộ phận" htmlFor="room-department" required>
        <select id="room-department" value={form.departmentId} disabled={Boolean(form.roomId || fixedDepartment) || saving} onChange={event => chooseDepartment(event.target.value)} className={controlClassName}>
          <option value="">Chọn khoa/bộ phận</option>
          {departments.filter(department => department.active || department.departmentId === form.departmentId).map(department => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}</option>)}
        </select>
      </Field>
      <Field label="Loại phòng" htmlFor="room-type" required hint={selectedType ? `${selectedType.description ? `${selectedType.description} ` : ""}${roomTypeRule(selectedType)}.` : !form.departmentId ? "Chọn khoa/bộ phận trước để thấy các loại phòng phù hợp." : formDepartment && allowedTypes.length === 0 ? `${departmentCategoryLabels[formDepartment.departmentType]} chưa có loại phòng nào trong danh mục.` : undefined}>
        <select id="room-type" value={form.roomType} disabled={Boolean(form.roomId) || !form.departmentId || saving} onChange={event => setForm(current => current && ({ ...current, roomType: event.target.value as RoomType }))} className={controlClassName}>
          <option value="">Chọn loại phòng</option>
          {form.roomId && form.roomType ? <option value={form.roomType}>{roomTypeName(types, form.roomType)}</option>
            : allowedTypes.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}
        </select>
      </Field>
      <Field label="Số phòng" htmlFor="room-number" required><input id="room-number" maxLength={20} value={form.roomNumber} disabled={saving} onChange={event => setForm(current => current && ({ ...current, roomNumber: event.target.value }))} placeholder="Ví dụ: 101" className={controlClassName} /></Field>
      <Field label="Tên phòng" htmlFor="room-name" required><input id="room-name" maxLength={100} value={form.roomName} disabled={saving} onChange={event => setForm(current => current && ({ ...current, roomName: event.target.value }))} placeholder="Ví dụ: Phòng khám Nội 1" className={controlClassName} /></Field>
      <Field label="Khu" htmlFor="room-zone" required><input id="room-zone" maxLength={50} value={form.zone} disabled={saving} onChange={event => setForm(current => current && ({ ...current, zone: event.target.value }))} placeholder="Ví dụ: Khu A" className={controlClassName} /></Field>
      <Field label="Vị trí / địa điểm" htmlFor="room-location" required><input id="room-location" maxLength={255} value={form.location} disabled={saving} onChange={event => setForm(current => current && ({ ...current, location: event.target.value }))} placeholder="Ví dụ: Tầng 1, Khu A" className={controlClassName} /></Field>
      {formError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger md:col-span-2 xl:col-span-3">{formError}</p> : null}
      <div className="flex flex-wrap gap-2 md:col-span-2 xl:col-span-3"><Button type="submit" variant="primary" loading={saving}>{form.roomId ? "Lưu thay đổi" : "Thêm phòng"}</Button><Button type="button" disabled={saving} onClick={() => { setForm(null); setFormError(null); }}>Hủy</Button></div>
    </form> : formError ? <p role="alert" className="mx-5 mb-4 text-sm text-danger">{formError}</p> : null}
    <div className="grid gap-4 border-t border-border p-4 md:grid-cols-3">
      {!fixedDepartment ? <Field label="Lọc theo" htmlFor="room-filter-by"><select id="room-filter-by" value={dimension} onChange={event => { setDimension(event.target.value as FilterDimension); setTarget(""); }} className={controlClassName}><option value="">Tất cả phòng</option><option value="department">Khoa / bộ phận</option><option value="type">Loại phòng</option></select></Field> : null}
      <Field label="Đối tượng lọc" htmlFor="room-filter-target" hint={!dimension ? "Chọn tiêu chí ở ô Lọc theo trước." : undefined}><select id="room-filter-target" value={target} disabled={!dimension} onChange={event => setTarget(event.target.value)} className={controlClassName}>
        <option value="">{dimension === "department" ? "Tất cả khoa/bộ phận" : dimension === "type" ? "Tất cả loại phòng" : "—"}</option>
        {dimension === "department" ? departmentCategories.map(category => {
          const members = departments.filter(department => department.departmentType === category);
          return members.length ? <optgroup key={category} label={departmentCategoryLabels[category]}>{members.map(department => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}</option>)}</optgroup> : null;
        }) : null}
        {dimension === "type" ? departmentCategories.map(category => {
          const members = filterTypes.filter(type => type.departmentType === category);
          return members.length ? <optgroup key={category} label={departmentCategoryLabels[category]}>{members.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}</optgroup> : null;
        }) : null}
      </select></Field>
      <Field label="Tìm phòng" htmlFor="room-keyword"><input id="room-keyword" type="search" value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="Số phòng, tên, khu" className={controlClassName} /></Field>
    </div>
    {error ? <AsyncState kind="error" message={error} onRetry={() => { setError(null); setRooms(null); setAttempt(value => value + 1); }} />
      : !rooms ? <AsyncState kind="loading" message="Đang tải phòng làm việc…" />
      : visibleRooms.length === 0 ? <AsyncState kind="empty" message="Chưa có phòng làm việc phù hợp." />
      : <div className="overflow-x-auto border-t border-border"><table className="w-full min-w-3xl text-left text-sm">
        <caption className="sr-only">{title}</caption>
        <thead className="border-b border-border text-muted-foreground"><tr>{["Số phòng", "Tên phòng", "Khu", "Vị trí", "Loại phòng", ...(fixedDepartment ? [] : ["Khoa / bộ phận"]), "Trạng thái", ...(admin ? ["Thao tác"] : [])].map(label => <th key={label} scope="col" className="px-4 py-3">{label}</th>)}</tr></thead>
        <tbody>{visibleRooms.map(room => <tr key={room.roomId} className="border-b border-border last:border-0">
          <td className="px-4 py-2 font-semibold tabular-nums">{room.roomNumber}</td>
          <td className="px-4 py-2">{room.roomName}</td>
          <td className="px-4 py-2">{room.zone}</td>
          <td className="px-4 py-2">{room.location}</td>
          <td className="px-4 py-2">{roomTypeName(types, room.roomType)}</td>
          {!fixedDepartment ? <td className="px-4 py-2">{departmentById.get(room.departmentId)?.departmentName ?? "—"}</td> : null}
          <td className="px-4 py-2"><StatusBadge tone={room.active ? "success" : "neutral"}>{room.active ? "Đang sử dụng" : "Ngừng sử dụng"}</StatusBadge></td>
          {admin ? <td className="px-4 py-2"><div className="flex flex-nowrap gap-2"><Button type="button" onClick={() => startEdit(room)} aria-label={`Sửa phòng ${room.roomNumber}`}>Sửa</Button><Button type="button" variant={room.active ? "danger" : "secondary"} onClick={() => void toggle(room)} aria-label={`${room.active ? "Ngừng" : "Mở lại"} phòng ${room.roomNumber}`}>{room.active ? "Ngừng" : "Mở lại"}</Button></div></td> : null}
        </tr>)}</tbody>
      </table></div>}
  </DirectorySection>;
}
