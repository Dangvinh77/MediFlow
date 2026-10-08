export type AdmissionPriority = "ROUTINE" | "URGENT" | "EMERGENCY";

export type AdmissionStatus =
  | "REQUESTED"
  | "AWAITING_BED"
  | "AWAITING_DEPOSIT"
  | "READY"
  | "ADMITTED"
  | "MEDICALLY_DISCHARGED"
  | "CLOSED"
  | "CANCELLED";

export type ClinicalOrderType = "LAB_TEST" | "PRESCRIPTION" | "SURGERY";

export type ExternalOrderStatus =
  | "REQUESTED"
  | "READY"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "FAILED";

export interface ClinicalOrderReferenceDTO {
  maThamChieu: string;
  maDotNoiTru: string;
  loaiYLenh: ClinicalOrderType;
  maYLenhBenNgoai: string;
  status: ExternalOrderStatus;
  tomTat: string | null;
  phienBanSuKien: number | null;
}

export interface AdmissionDTO {
  maDotNoiTru: string;
  maYeuCauNoiTru: string;
  maBenhNhan: string;
  maHoSoNguon: string;
  maKhoa: string;
  doUuTien: AdmissionPriority;
  capCuu: boolean;
  status: AdmissionStatus;
  maGiuongDangSuDung: string | null;
  maXacNhanTamUng: string | null;
  maQuyetToan: string | null;
  maTomTatRaVien: string | null;
  maPheDuyetNgoaiLe: string | null;
  thoiGianYeuCau: string;
  thoiGianNhapVien: string | null;
  thoiGianRaVienYTe: string | null;
  thoiGianDong: string | null;
  thoiGianHuy: string | null;
  lyDoHuy: string | null;
  yeuLenhNgoai: ClinicalOrderReferenceDTO[];
}

export type BedStatus = "AVAILABLE" | "OCCUPIED" | "OUT_OF_SERVICE";

export interface BedDTO {
  maGiuong: string;
  maKhoa: string;
  maKhu: string;
  maPhong: string;
  maGiuongTrongPhong: string;
  loaiGiuong: string;
  status: BedStatus;
  active: boolean;
}
