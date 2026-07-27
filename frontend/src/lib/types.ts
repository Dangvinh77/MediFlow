// Types mirroring the backend contracts. Keep field names in Vietnamese camelCase
// to match the services (see docs/ai/05-api-conventions.md).

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
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

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
