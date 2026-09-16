// Minimal client-side auth. NOTE: localStorage is convenient for a starter but is
// vulnerable to XSS — for production prefer httpOnly cookies. See docs/ai/12-frontend.md.

import { api, ApiRequestError } from "./api";
import type { Role } from "./roles";
import {
  clearSession,
  getRole as readRole,
  getToken as readToken,
  storeSession,
} from "./session";

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
  return getToken() !== null;
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  try {
    const response = await api.postRaw<LoginResponse>("/v1/auth/login", {
      username,
      password,
    });
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
