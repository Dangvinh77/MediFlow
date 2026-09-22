// Minimal client-side auth. NOTE: localStorage is convenient for a starter but is
// vulnerable to XSS — for production prefer httpOnly cookies. See docs/ai/12-frontend.md.

import { api, ApiRequestError } from "./api";
import { isRole, type Role } from "./roles";
import {
  clearSession,
  getRole as readRole,
  getToken as readToken,
  storeSession,
} from "./session";

export { subscribeToAuthChanges } from "./session";

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  role: Role;
}

export function getToken(): string | null {
  return readToken();
}

export function getRole(): Role | null {
  return readRole();
}

export function isAuthenticated(): boolean {
  const token = getToken();
  return token !== null && token.trim().length > 0;
}

function isLoginResponse(value: unknown): value is LoginResponse {
  if (typeof value !== "object" || value === null) return false;

  const response = value as Record<string, unknown>;
  return (
    typeof response.accessToken === "string" &&
    response.accessToken.trim().length > 0 &&
    typeof response.refreshToken === "string" &&
    response.refreshToken.trim().length > 0 &&
    typeof response.role === "string" &&
    isRole(response.role)
  );
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  try {
    const response = await api.postRaw<LoginResponse>("/v1/auth/login", {
      username,
      password,
    });

    if (!isLoginResponse(response)) {
      throw new Error("Phản hồi đăng nhập không hợp lệ.");
    }

    storeSession(response);
    return response;
  } catch (cause: unknown) {
    if (cause instanceof ApiRequestError && cause.status === 401) {
      throw new Error("Đăng nhập thất bại — sai tài khoản hoặc mật khẩu.");
    }
    throw cause;
  }
}

export function logout() {
  clearSession();
}
