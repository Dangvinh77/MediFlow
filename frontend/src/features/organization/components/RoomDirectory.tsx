"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


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
export function RoomDirectory({ fixedDepartment, onRoomsChanged }: { fixedDepartment?: DepartmentDTO; onRoomsChanged?: () => void }) {
  const { localize } = useLocale();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const admin = role === "ADMIN";
  const [departments, setDepartments] = useState<DepartmentDTO[]>(fixedDepartment ? [fixedDepartment] : []);
  const [rooms, setRooms] = useState<RoomDTO[] | null>(null);
  const [types, setTypes] = useState<RoomTypeDTO[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [dimension, setDimension] = useState<FilterDimension>("");
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
  // Inside a department every room listed already belongs to it, so there is nothing to filter.
  const visibleRooms = fixedDepartment ? rooms ?? [] : (rooms ?? []).filter(room => (!departmentFilter || room.departmentId === departmentFilter)
    && (!typeFilter || room.roomType === typeFilter)
    && normalize(`${room.roomNumber} ${room.roomName} ${room.zone} ${room.location}`).includes(normalize(keyword.trim())));
  const formDepartment = form ? departmentById.get(form.departmentId) : undefined;
  const allowedTypes = roomTypesFor(types, formDepartment?.departmentType);
  const selectedType = form ? types.find(type => type.code === form.roomType) : undefined;

  function startCreate() {
    setNotice(null); setFormError(null);
    const departmentId = fixedDepartment?.departmentId ?? departmentFilter;
    const choices = roomTypesFor(types, departmentById.get(departmentId)?.departmentType).map(type => type.code);
    // Preselect the only fitting type and default the location to the department's own.
    setForm({ ...emptyForm(departmentId), roomType: choices.length === 1 ? choices[0] : "", location: departmentById.get(departmentId)?.location ?? "" });
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
      onRoomsChanged?.();
    } catch (cause) {
      setFormError(messageFrom(cause, "Không thể lưu phòng làm việc."));
    } finally { setSaving(false); }
  }

  async function toggle(room: RoomDTO) {
    setNotice(null); setError(null);
    if (room.active && !window.confirm(localize(`Ngừng sử dụng phòng ${room.roomNumber} – ${room.roomName}? Phòng sẽ không nhận đăng ký khám mới; bệnh nhân đã đặt lịch tại phòng này cần được dời lịch.`))) return;
    try {
      const saved = await organizationApi.changeRoomStatus(room.roomId, !room.active);
      setRooms(current => current?.map(item => item.roomId === saved.roomId ? saved : item) ?? null);
      setNotice(saved.active ? "Đã mở lại phòng." : "Đã ngừng sử dụng phòng.");
      onRoomsChanged?.();
    } catch (cause) {
      setNotice(null); setFormError(messageFrom(cause, "Không thể đổi trạng thái phòng."));
    }
  }

  const title = fixedDepartment ? "Phòng làm việc của đơn vị" : "Phòng làm việc";
  // An operating clinical department needs its office and at least two consultation rooms.
  const readiness = fixedDepartment?.departmentType === "CLINICAL" && rooms ? {
    office: rooms.some(room => room.active && room.roomType === "DEPARTMENT_OFFICE"),
    consultations: rooms.filter(room => room.active && room.roomType === "CONSULTATION").length,
  } : null;
  const description = fixedDepartment
    ? fixedDepartment.departmentType === "CLINICAL"
      ? "Các phòng khám được hoạch định cho khoa và văn phòng chung của khoa."
      : "Bộ phận chức năng làm việc chung tại một phòng; mọi nhân sự đều làm việc tại đây."
    : "Phòng khám phân bổ cho khoa, văn phòng khoa và nơi làm việc của các khoa cận lâm sàng và phòng chức năng. Loại phòng được quản lý tại Danh mục khoa phòng.";

  return <DirectorySection title={localize(title)} description={localize(description)} actions={admin && !form ? <Button variant="primary" onClick={startCreate}><L10n>{"Thêm phòng làm việc"}</L10n></Button> : undefined}>
    <L10nValue>{readiness ? <div aria-label={localize("Điều kiện hoạt động của khoa")} className={`mx-5 mb-4 rounded-lg border p-3 text-sm ${readiness.office && readiness.consultations >= 2 ? "border-success/40 bg-success/10" : "border-warning/40 bg-warning/10"}`}>
      <p className="font-semibold"><L10nValue>{readiness.office && readiness.consultations >= 2 ? "Khoa đủ điều kiện hoạt động" : "Khoa chưa đủ điều kiện hoạt động"}</L10nValue></p>
      <ul className="mt-1 list-disc pl-5">
        <li><L10nValue>{readiness.office ? "Đã có văn phòng khoa – địa điểm khoa lấy theo văn phòng này." : "Chưa có văn phòng khoa – địa điểm khoa đang để trống."}</L10nValue></li>
        <li><L10n>{`Phòng khám đang sử dụng: ${readiness.consultations}/2 tối thiểu`}</L10n></li>
      </ul>
    </div> : null}</L10nValue>
    <L10nValue>{notice ? <p role="status" className="mx-5 mb-4 rounded-lg border border-success/40 bg-success/10 p-3 text-sm text-success"><L10nValue>{notice}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{form ? <form aria-label={localize(form.roomId ? "Sửa phòng làm việc" : "Thêm phòng làm việc")} onSubmit={save} noValidate className="grid items-start gap-4 border-t border-border p-5 md:grid-cols-2">
      <L10nValue>{fixedDepartment ? null : <Field label={localize("Khoa / bộ phận")} htmlFor="room-department" required>
        <select id="room-department" value={form.departmentId} disabled={Boolean(form.roomId || fixedDepartment) || saving} onChange={event => chooseDepartment(event.target.value)} className={controlClassName}>
          <option value=""><L10n>{"Chọn khoa/bộ phận"}</L10n></option>
          <L10nValue>{departments.filter(department => department.active || department.departmentId === form.departmentId).map(department => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}</option>)}</L10nValue>
        </select>
      </Field>}</L10nValue>
      <Field label={localize("Loại phòng")} htmlFor="room-type" required hint={selectedType ? `${selectedType.description ? `${selectedType.description} ` : ""}${roomTypeRule(selectedType)}.` : !form.departmentId ? "Chọn khoa/bộ phận trước để thấy các loại phòng phù hợp." : formDepartment && allowedTypes.length === 0 ? `${departmentCategoryLabels[formDepartment.departmentType]} chưa có loại phòng nào trong danh mục.` : undefined}>
        <select id="room-type" value={form.roomType} disabled={Boolean(form.roomId) || !form.departmentId || saving} onChange={event => setForm(current => current && ({ ...current, roomType: event.target.value as RoomType }))} className={controlClassName}>
          <option value=""><L10n>{"Chọn loại phòng"}</L10n></option>
          <L10nValue>{form.roomId && form.roomType ? <option value={form.roomType}><L10nValue>{roomTypeName(types, form.roomType)}</L10nValue></option>
            : allowedTypes.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}</L10nValue>
        </select>
      </Field>
      <Field label={localize("Số phòng")} htmlFor="room-number" required><input id="room-number" maxLength={20} value={form.roomNumber} disabled={saving} onChange={event => setForm(current => current && ({ ...current, roomNumber: event.target.value }))} placeholder={localize("Ví dụ: 101")} className={controlClassName} /></Field>
      <Field label={localize("Tên phòng")} htmlFor="room-name" required><input id="room-name" maxLength={100} value={form.roomName} disabled={saving} onChange={event => setForm(current => current && ({ ...current, roomName: event.target.value }))} placeholder={localize("Ví dụ: Phòng khám Nội 1")} className={controlClassName} /></Field>
      <Field label="Khu" htmlFor="room-zone" required><input id="room-zone" maxLength={50} value={form.zone} disabled={saving} onChange={event => setForm(current => current && ({ ...current, zone: event.target.value }))} placeholder={localize("Ví dụ: Khu A")} className={controlClassName} /></Field>
      <Field label={localize("Vị trí / địa điểm")} htmlFor="room-location" required><input id="room-location" maxLength={255} value={form.location} disabled={saving} onChange={event => setForm(current => current && ({ ...current, location: event.target.value }))} placeholder={localize("Ví dụ: Tầng 1, Khu A")} className={controlClassName} /></Field>
      <L10nValue>{formError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger md:col-span-2"><L10nValue>{formError}</L10nValue></p> : null}</L10nValue>
      <div className="flex flex-wrap gap-2 md:col-span-2"><Button type="submit" variant="primary" loading={saving}><L10nValue>{form.roomId ? "Lưu thay đổi" : "Thêm phòng"}</L10nValue></Button><Button type="button" disabled={saving} onClick={() => { setForm(null); setFormError(null); }}><L10n>{"Hủy"}</L10n></Button></div>
    </form> : formError ? <p role="alert" className="mx-5 mb-4 text-sm text-danger"><L10nValue>{formError}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{fixedDepartment ? null : <div className="grid gap-4 border-t border-border p-4 md:grid-cols-3">
      <Field label={localize("Lọc theo")} htmlFor="room-filter-by"><select id="room-filter-by" value={dimension} onChange={event => { setDimension(event.target.value as FilterDimension); setTarget(""); }} className={controlClassName}><option value=""><L10n>{"Tất cả phòng"}</L10n></option><option value="department"><L10n>{"Khoa / bộ phận"}</L10n></option><option value="type"><L10n>{"Loại phòng"}</L10n></option></select></Field>
      <Field label={localize("Đối tượng lọc")} htmlFor="room-filter-target" hint={!dimension ? "Chọn tiêu chí ở ô Lọc theo trước." : undefined}><select id="room-filter-target" value={target} disabled={!dimension} onChange={event => setTarget(event.target.value)} className={controlClassName}>
        <option value=""><L10nValue>{dimension === "department" ? "Tất cả khoa/bộ phận" : dimension === "type" ? "Tất cả loại phòng" : "—"}</L10nValue></option>
        <L10nValue>{dimension === "department" ? departmentCategories.map(category => {
          const members = departments.filter(department => department.departmentType === category);
          return members.length ? <optgroup key={category} label={localize(departmentCategoryLabels[category])}><L10nValue>{members.map(department => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}</option>)}</L10nValue></optgroup> : null;
        }) : null}</L10nValue>
        <L10nValue>{dimension === "type" ? departmentCategories.map(category => {
          const members = filterTypes.filter(type => type.departmentType === category);
          return members.length ? <optgroup key={category} label={localize(departmentCategoryLabels[category])}><L10nValue>{members.map(type => <option key={type.code} value={type.code}>{type.name}</option>)}</L10nValue></optgroup> : null;
        }) : null}</L10nValue>
      </select></Field>
      <Field label={localize("Tìm phòng")} htmlFor="room-keyword"><input id="room-keyword" type="search" value={keyword} onChange={event => setKeyword(event.target.value)} placeholder={localize("Số phòng, tên, khu")} className={controlClassName} /></Field>
    </div>}</L10nValue>
    <L10nValue>{error ? <AsyncState kind="error" message={localize(error)} onRetry={() => { setError(null); setRooms(null); setAttempt(value => value + 1); }} />
      : !rooms ? <AsyncState kind="loading" message={localize("Đang tải phòng làm việc…")} />
      : visibleRooms.length === 0 ? <AsyncState kind="empty" message={localize("Chưa có phòng làm việc phù hợp.")} />
      : <div className="overflow-x-auto border-t border-border"><table className={`w-full text-left text-sm ${fixedDepartment ? "" : "min-w-3xl"}`}>
        <caption className="sr-only"><L10nValue>{title}</L10nValue></caption>
        <thead className="border-b border-border text-muted-foreground"><tr><L10nValue>{["Số phòng", "Tên phòng", ...(fixedDepartment ? ["Vị trí"] : ["Khu", "Vị trí"]), "Loại phòng", ...(fixedDepartment ? [] : ["Khoa / bộ phận"]), "Trạng thái", ...(admin ? ["Thao tác"] : [])].map(label => <th key={label} scope="col" className="whitespace-nowrap px-3 py-3"><L10nValue>{label}</L10nValue></th>)}</L10nValue></tr></thead>
        <tbody><L10nValue>{visibleRooms.map(room => <tr key={room.roomId} className="border-b border-border last:border-0">
          <td className="whitespace-nowrap px-3 py-2 font-semibold tabular-nums">{room.roomNumber}</td>
          <td className="px-3 py-2">{room.roomName}</td>
          <L10nValue>{fixedDepartment ? <td className="px-3 py-2">{room.location}{normalize(room.location).includes(normalize(room.zone)) ? null : <span className="block text-xs text-muted-foreground">{room.zone}</span>}</td>
            : <><td className="px-3 py-2">{room.zone}</td><td className="px-3 py-2">{room.location}</td></>}</L10nValue>
          <td className="px-3 py-2"><L10nValue>{roomTypeName(types, room.roomType)}</L10nValue></td>
          <L10nValue>{!fixedDepartment ? <td className="px-3 py-2"><L10nValue>{departmentById.get(room.departmentId)?.departmentName ?? "—"}</L10nValue></td> : null}</L10nValue>
          <td className="whitespace-nowrap px-3 py-2"><StatusBadge tone={room.active ? "success" : "neutral"}><L10nValue>{room.active ? "Đang sử dụng" : "Ngừng sử dụng"}</L10nValue></StatusBadge></td>
          <L10nValue>{admin ? <td className="whitespace-nowrap px-3 py-2"><div className="flex flex-nowrap gap-2"><Button type="button" onClick={() => startEdit(room)} aria-label={localize(`Sửa phòng ${room.roomNumber}`)}><L10n>{"Sửa"}</L10n></Button><Button type="button" variant={room.active ? "danger" : "secondary"} onClick={() => void toggle(room)} aria-label={localize(`${room.active ? "Ngừng" : "Mở lại"} phòng ${room.roomNumber}`)}><L10nValue>{room.active ? "Ngừng" : "Mở lại"}</L10nValue></Button></div></td> : null}</L10nValue>
        </tr>)}</L10nValue></tbody>
      </table></div>}</L10nValue>
  </DirectorySection>;
}
