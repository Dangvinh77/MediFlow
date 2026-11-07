import type { DepartmentType, RoomDTO, RoomType } from "./types";

/** Vietnamese labels for Core's workplace room catalogue. */
export const roomTypeLabels: Record<RoomType, string> = {
  CONSULTATION: "Phòng khám",
  DEPARTMENT_OFFICE: "Văn phòng khoa (giao ban)",
  LABORATORY: "Phòng xét nghiệm",
  PHARMACY: "Quầy phát thuốc",
  CASHIER: "Quầy thu ngân",
  NURSING: "Phòng điều dưỡng",
  ADMINISTRATION: "Phòng quản lý / hành chính",
};

export const roomTypeHints: Record<RoomType, string> = {
  CONSULTATION: "Phòng khám phân bổ riêng cho một khoa lâm sàng; một khoa có thể có nhiều phòng khám và chỉ loại này được dùng để lập buổi khám.",
  DEPARTMENT_OFFICE: "Nơi làm việc chung, giao ban của khoa lâm sàng; mang tính biểu tượng, không dùng để lập buổi khám. Mỗi khoa một phòng.",
  LABORATORY: "Nơi làm việc duy nhất của khoa xét nghiệm; mọi kỹ thuật viên làm việc tại đây.",
  PHARMACY: "Nơi cấp phát thuốc ngoại trú duy nhất của khoa dược.",
  CASHIER: "Quầy thu ngân duy nhất của bộ phận tài chính kế toán.",
  NURSING: "Nơi làm việc duy nhất của phòng điều dưỡng (tiếp đón, đăng ký khám).",
  ADMINISTRATION: "Nơi làm việc duy nhất của phòng hành chính – quản trị hệ thống.",
};

export const roomTypes = Object.keys(roomTypeLabels) as RoomType[];

/** Mirrors RoomType.allowedFor in Core; the backend rechecks every write. */
export function roomTypesFor(departmentType: DepartmentType | undefined): RoomType[] {
  switch (departmentType) {
    case "CLINICAL": return ["CONSULTATION", "DEPARTMENT_OFFICE"];
    case "PARACLINICAL": return ["LABORATORY", "PHARMACY"];
    case "ADMINISTRATIVE": return ["CASHIER", "NURSING", "ADMINISTRATION"];
    default: return [];
  }
}

export function roomLabel(room: Pick<RoomDTO, "roomNumber" | "roomName" | "zone">): string {
  return `P.${room.roomNumber} · ${room.roomName} · ${room.zone}`;
}

export const roomErrorMessages: Record<string, string> = {
  ROOM_TYPE_NOT_ALLOWED: "Loại phòng không phù hợp với loại khoa/bộ phận: phòng khám và văn phòng khoa thuộc khoa lâm sàng; phòng xét nghiệm, quầy phát thuốc thuộc khoa cận lâm sàng; thu ngân, điều dưỡng, quản lý thuộc phòng chức năng.",
  ROOM_SINGLE_WORKPLACE: "Bộ phận này đã có phòng làm việc đang hoạt động. Mỗi bộ phận chức năng chỉ làm việc tại một phòng; hãy ngừng phòng cũ trước.",
  ROOM_TYPE_ALREADY_ASSIGNED: "Khoa đã có văn phòng khoa đang hoạt động. Mỗi khoa chỉ có một văn phòng khoa.",
  ROOM_NUMBER_DUPLICATE: "Số phòng đã tồn tại trong khu này.",
  ROOM_DEPARTMENT_INACTIVE: "Khoa/bộ phận đã ngừng hoạt động nên không thể gán phòng.",
  ROOM_NOT_FOUND: "Không tìm thấy phòng làm việc.",
};
