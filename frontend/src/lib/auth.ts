// Minimal client-side auth: talks to the gateway's /api/v1/auth endpoints and stores
// the JWT in localStorage. NOTE: localStorage is convenient for a starter but is
// vulnerable to XSS — for production prefer httpOnly cookies. See docs/ai/12-frontend.md.

import type { LoginResponse } from "./types";

const ACCESS_TOKEN_KEY = "mediflow.accessToken";
const REFRESH_TOKEN_KEY = "mediflow.refreshToken";
const ROLE_KEY = "mediflow.role";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRole(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ROLE_KEY);
}

export function isAuthenticated(): boolean {
  return getToken() !== null;
}

function store(res: LoginResponse) {
  localStorage.setItem(ACCESS_TOKEN_KEY, res.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, res.refreshToken);
  localStorage.setItem(ROLE_KEY, res.role);
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    throw new Error("Đăng nhập thất bại — sai tài khoản hoặc mật khẩu.");
  }
  const data = (await res.json()) as LoginResponse;
  store(data);
  return data;
}

export function logout() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
}
