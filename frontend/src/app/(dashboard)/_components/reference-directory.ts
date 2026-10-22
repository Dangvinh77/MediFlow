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
  const orgReader = clinicalReader || role === "MANAGER";
  const canSearch = (kind: string) => kind === "patient" || kind === "record" ? clinicalReader
    : ["doctor", "department", "staff", "appointment"].includes(kind) && orgReader;
  const save = (kind: string, items: { id: string; label: string }[]) => {
    for (const item of items) labels.set(`${kind}:${item.id}`, Promise.resolve(item.label));
    return items;
  };
  return {
    canSearch,
    async search(kind, query, page, scope) {
      if (!canSearch(kind)) throw new Error("Bạn chưa có quyền tìm kiếm thông tin này.");
      if (kind === "patient") {
        const result = await patientApi.search({ keyword: query, page, size: 20 });
        return { items: save(kind, result.content.map(patient => ({ id: patient.maBenhNhan, label: `${patient.hoTen} · ${formatLocalDate(patient.ngaySinh)}` }))), hasMore: result.number + 1 < result.totalPages };
      }
      if (kind === "department") {
        const items = (await organizationApi.departments(true)).filter(item => `${item.departmentName} ${item.abbreviation}`.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi")))
          .map(item => ({ id: item.departmentId, label: `${item.departmentName} (${item.abbreviation})` }));
        return { items: save(kind, items.slice(page * 20, (page + 1) * 20)), hasMore: items.length > (page + 1) * 20 };
      }
      if (kind === "doctor" || kind === "staff") {
        const result = await organizationApi.staff({ departmentId: scope || undefined, jobTitle: kind === "doctor" ? "DOCTOR" : undefined, page, size: 20 });
        return { items: save(kind, result.content.filter(item => item.active && item.fullName.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi"))).map(item => ({ id: item.staffId, label: `${item.fullName}${item.specialization ? ` · ${item.specialization}` : ""}` }))), hasMore: result.number + 1 < result.totalPages, hint: "Tên được lọc trong từng trang. Dùng Trang sau để xem thêm nhân viên." };
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
      if (!canSearch(kind)) return Promise.resolve(null);
      const key = `${kind}:${id}`;
      const cached = labels.get(key);
      if (cached) return cached;
      const pending = (async () => {
        if (kind === "patient") { const item = await patientApi.getById(id); return item.maBenhNhan === id ? `${item.hoTen} · ${formatLocalDate(item.ngaySinh)}` : null; }
        if (kind === "doctor" || kind === "staff") { const item = await organizationApi.staffMember(id); return item.staffId === id ? item.fullName : null; }
        if (kind === "department") { const item = await organizationApi.department(id); return item.departmentId === id ? item.departmentName : null; }
        if (kind === "appointment" && clinicalReader) { const item = await appointmentApi.getById(id); return item.appointmentId === id ? `Lịch hẹn ${formatLocalDate(item.appointmentDate)} · ${item.appointmentTime}` : null; }
        if (kind === "record") { const item = await medicalRecordApi.getById(id); return item.recordId === id ? `Hồ sơ ${formatLocalDate(item.examinationDate)}` : null; }
        return null;
      })().catch(error => { labels.delete(key); throw error; });
      labels.set(key, pending);
      return pending;
    },
  };
}
