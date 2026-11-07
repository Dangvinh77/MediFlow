import type { DepartmentType, RoomDTO, RoomType, RoomTypeDTO } from "./types";

/**
 * The three fixed department categories of a Vietnamese hospital (Quy chế bệnh viện): scheduling,
 * head appointment and other services depend on them, so only room types are configurable.
 */
export const departmentCategories: DepartmentType[] = ["CLINICAL", "PARACLINICAL", "ADMINISTRATIVE"];

export const departmentCategoryLabels: Record<DepartmentType, string> = {
  CLINICAL: "Khối lâm sàng",
  PARACLINICAL: "Khối cận lâm sàng",
  ADMINISTRATIVE: "Khối hành chính (phòng chức năng)",
};

export const departmentCategoryHints: Record<DepartmentType, string> = {
  CLINICAL: "Khoa khám bệnh theo chuyên khoa; có trưởng khoa, bác sĩ và điều dưỡng; có nhiều phòng khám.",
  PARACLINICAL: "Khoa hỗ trợ chẩn đoán và điều trị như Khoa Xét nghiệm, Khoa Dược; làm việc tại một phòng.",
  ADMINISTRATIVE: "Phòng chức năng như Hành chính quản trị, Tài chính kế toán; làm việc tại một phòng.",
};

/** Room types of a category, in catalogue order. The backend rechecks every write. */
export function roomTypesFor(types: RoomTypeDTO[], departmentType: DepartmentType | undefined): RoomTypeDTO[] {
  return departmentType ? types.filter(type => type.departmentType === departmentType) : [];
}

export function roomTypeName(types: RoomTypeDTO[], code: RoomType): string {
  return types.find(type => type.code === code)?.name ?? code;
}

export function roomTypeRule(type: RoomTypeDTO): string {
  if (type.departmentType !== "CLINICAL") return "Bộ phận làm việc tại đúng một phòng";
  return type.singlePerDepartment ? "Mỗi khoa một phòng" : "Mỗi khoa có thể có nhiều phòng";
}

export function roomLabel(room: Pick<RoomDTO, "roomNumber" | "roomName" | "zone">): string {
  return `P.${room.roomNumber} · ${room.roomName} · ${room.zone}`;
}

export const roomErrorMessages: Record<string, string> = {
  ROOM_TYPE_NOT_ALLOWED: "Loại phòng không thuộc khối của khoa/bộ phận này. Xem các loại phòng của từng khối tại Danh mục khoa phòng.",
  ROOM_TYPE_UNKNOWN: "Loại phòng không còn trong danh mục. Tải lại trang và chọn loại phòng khác.",
  ROOM_SINGLE_WORKPLACE: "Bộ phận này đã có phòng làm việc đang hoạt động. Mỗi bộ phận khối cận lâm sàng hoặc hành chính chỉ làm việc tại một phòng; hãy ngừng phòng cũ trước.",
  ROOM_TYPE_ALREADY_ASSIGNED: "Khoa đã có một phòng đang hoạt động thuộc loại này; loại phòng này chỉ cho phép một phòng mỗi khoa.",
  ROOM_NUMBER_DUPLICATE: "Số phòng đã tồn tại trong khu này.",
  ROOM_DEPARTMENT_INACTIVE: "Khoa/bộ phận đã ngừng hoạt động nên không thể gán phòng.",
  ROOM_NOT_FOUND: "Không tìm thấy phòng làm việc.",
  ROOM_TYPE_DUPLICATE: "Tên loại phòng đã tồn tại trong danh mục.",
  ROOM_TYPE_IN_USE: "Loại phòng đang được phòng làm việc sử dụng (kể cả phòng đã ngừng) nên không thể xóa.",
  ROOM_TYPE_SYSTEM: "Phòng khám là loại phòng hệ thống dùng để lập buổi khám nên không thể xóa.",
  ROOM_TYPE_NOT_FOUND: "Loại phòng không còn trong danh mục.",
};
