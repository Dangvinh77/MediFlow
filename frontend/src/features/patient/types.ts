export type GioiTinh = "M" | "F";

export interface PatientDTO {
  maBenhNhan: string;
  hoTen: string;
  ngaySinh: string;
  gioiTinh: GioiTinh;
  soCmnd: string;
  diaChi: string | null;
  soDienThoai: string | null;
  email: string | null;
  bhytSo: string | null;
  createdAt: string;
  updatedAt: string;
  daLuuTru?: boolean;
}

export interface CreatePatientRequest {
  hoTen: string;
  ngaySinh: string;
  gioiTinh: GioiTinh;
  soCmnd: string;
  diaChi: string | null;
  soDienThoai: string | null;
  email: string | null;
  bhytSo: string | null;
}

/** Omitted/null soCmnd preserves the current number for older clients. */
export type UpdatePatientRequest = Omit<CreatePatientRequest, "soCmnd"> & { soCmnd?: string | null; lyDoThayDoiDinhDanh?: string | null };
