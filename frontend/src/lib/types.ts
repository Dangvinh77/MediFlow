// Shared envelope types plus the legacy Patient demo contract. Feature DTOs live
// under features/<context> and preserve each service's exact wire field names.

/** Standard response envelope returned by every MediFlow service. */
export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ApiError | null;
  timestamp: string;
  correlationId: string | null;
}

export interface ApiError {
  code: string;
  message: string;
  details: { field: string; message: string }[];
}

/** Spring Data page shape (as serialized into ApiResponse.data). */
export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** Compatibility alias for the existing Patient demo. */
export type Page<T> = PageResult<T>;

export type GioiTinh = "M" | "F";

export interface PatientDTO {
  maBenhNhan: string;
  hoTen: string;
  ngaySinh: string; // ISO date
  gioiTinh: GioiTinh;
  soCmnd: string;
  diaChi?: string;
  soDienThoai?: string;
  email?: string;
  bhytSo?: string;
  createdAt: string;
  updatedAt: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  role: string;
}
