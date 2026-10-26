import type { ReferenceDirectory } from "@/components/references/ReferenceProvider";
import type { Role } from "@/lib/roles";
import { patientApi } from "@/features/patient/api";
import { organizationApi } from "@/features/organization/api";
import { appointmentApi } from "@/features/appointment/api";
import { medicalRecordApi } from "@/features/medical-record/api";
import { formatLocalDate } from "@/lib/format";

// Composition only: labels never establish clinical or financial authority.
export function createReferenceDirectory(role: Role | null): ReferenceDirectory {
  const labels = new Map<string, Promise<string | null>>();
  const clinicalReader = role !== null && ["ADMIN", "DOCTOR", "NURSE"].includes(role);
  const minimalPatientReader = role === "CASHIER" || role === "LAB_TECH" || role === "PHARMACIST";
  const orgReader = clinicalReader || role === "MANAGER";
  const canSearch = (kind: string) => kind === "account" ? role === "ADMIN"
    : kind === "patient" ? clinicalReader || minimalPatientReader
      : kind === "record" ? clinicalReader
      : ["doctor", "department", "staff", "appointment"].includes(kind) && orgReader;
  const careKinds = ["doctor", "department", "room"] as const;
  const isCareKind = (kind: string): kind is typeof careKinds[number] => careKinds.some(value => value === kind);
  const canResolve = (kind: string) => role !== null && role !== "SYSTEM" && (isCareKind(kind) || canSearch(kind));
  const save = (kind: string, items: { id: string; label: string }[]) => {
    for (const item of items) labels.set(`${kind}:${item.id}`, Promise.resolve(item.label));
    return items;
  };
  return {
    canSearch,
    canResolve,
    async search(kind, query, page, scope) {
      if (!canSearch(kind)) throw new Error("Bạn chưa có quyền tìm kiếm thông tin này.");
      if (kind === "patient") {
        const result = minimalPatientReader ? await patientApi.directory(query, page) : await patientApi.search({ keyword: query, page, size: 20 });
        return { items: save(kind, result.content.map(patient => ({ id: patient.maBenhNhan, label: `${patient.hoTen} · ${formatLocalDate(patient.ngaySinh)}` }))), hasMore: result.number + 1 < result.totalPages };
      }
      if (kind === "department") {
        const items = (await organizationApi.departments(true)).filter(item => `${item.departmentName} ${item.abbreviation}`.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi")))
          .map(item => ({ id: item.departmentId, label: `${item.departmentName} (${item.abbreviation})` }));
        return { items: save(kind, items.slice(page * 20, (page + 1) * 20)), hasMore: items.length > (page + 1) * 20 };
      }
      if (kind === "account") {
        const result = await organizationApi.accounts({ keyword: query, role: "MANAGER", active: true, page });
        return {
          items: save(kind, result.content.map(item => ({ id: item.accountId, label: `${item.username} · ${item.role}` }))),
          hasMore: result.number + 1 < result.totalPages,
        };
      }
      if (kind === "doctor" || kind === "staff") {
        const result = await organizationApi.staff({ departmentId: scope || undefined, jobTitle: kind === "doctor" ? "DOCTOR" : undefined, keyword: query, activeOnly: true, page, size: 20 });
        return { items: save(kind, result.content.filter(item => item.active).map(item => ({ id: item.staffId, label: `${item.fullName}${item.specialization ? ` · ${item.specialization}` : ""}` }))), hasMore: result.number + 1 < result.totalPages };
      }
      if (kind === "appointment") {
        const result = await appointmentApi.search({ page, size: 20 });
        return { items: save(kind, result.content.filter(item => !scope || item.patientId === scope).filter(item => `${item.appointmentDate} ${item.reason ?? ""}`.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi"))).map(item => ({ id: item.appointmentId, label: `Lịch hẹn ${formatLocalDate(item.appointmentDate)} · ${item.appointmentTime}${item.reason ? ` · ${item.reason}` : ""}` }))), hasMore: result.number + 1 < result.totalPages, hint: "Lịch hẹn được lọc trong từng trang theo bệnh nhân đã chọn. Dùng Trang sau để xem thêm." };
      }
      if (kind === "record" && scope) {
        const records = await medicalRecordApi.byPatient(scope);
        const items = records.map(item => ({ id: item.recordId, label: `Hồ sơ ${formatLocalDate(item.examinationDate)}${item.symptoms ? ` · ${item.symptoms}` : ""}` })).filter(item => item.label.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi")));
        return { items: save(kind, items.slice(page * 20, (page + 1) * 20)), hasMore: items.length > (page + 1) * 20 };
      }
      throw new Error("Hãy chọn bệnh nhân trước khi tìm hồ sơ.");
    },
    resolve(kind, id) {
      if (!canResolve(kind)) return Promise.resolve(null);
      const key = `${kind}:${id}`;
      const cached = labels.get(key);
      if (cached) return cached;
      const pending = (async () => {
        if (isCareKind(kind)) { const item = await organizationApi.careLabel(kind, id); return item.id === id ? item.label : null; }
        if (kind === "patient") { const item = minimalPatientReader ? await patientApi.directoryEntry(id) : await patientApi.getById(id); return item.maBenhNhan === id ? `${item.hoTen} · ${formatLocalDate(item.ngaySinh)}` : null; }
        if (kind === "doctor" || kind === "staff") { const item = await organizationApi.staffMember(id); return item.staffId === id ? item.fullName : null; }
        if (kind === "department") { const item = await organizationApi.department(id); return item.departmentId === id ? item.departmentName : null; }
        if (kind === "account" && role === "ADMIN") { const item = await organizationApi.account(id); return item.accountId === id ? `${item.username} · ${item.role}` : null; }
        if (kind === "appointment" && clinicalReader) { const item = await appointmentApi.getById(id); return item.appointmentId === id ? `Lịch hẹn ${formatLocalDate(item.appointmentDate)} · ${item.appointmentTime}` : null; }
        if (kind === "record") { const item = await medicalRecordApi.getById(id); return item.recordId === id ? `Hồ sơ ${formatLocalDate(item.examinationDate)}` : null; }
        return null;
      })().then(label => { if (label === null) labels.delete(key); return label; }).catch(error => { labels.delete(key); throw error; });
      labels.set(key, pending);
      return pending;
    },
  };
}
