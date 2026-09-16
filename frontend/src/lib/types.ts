// Shared transport types only. Feature DTOs live under features/<context>.

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
