import type { Role } from "./roles";
import { isRole } from "./roles";

const ACCESS_TOKEN_KEY = "mediflow.accessToken";
const REFRESH_TOKEN_KEY = "mediflow.refreshToken";
const ROLE_KEY = "mediflow.role";

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  role: Role;
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRole(): Role | null {
  if (typeof window === "undefined") return null;
  const role = localStorage.getItem(ROLE_KEY);
  return role && isRole(role) ? role : null;
}

export function storeSession(session: AuthSession) {
  localStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
  localStorage.setItem(ROLE_KEY, session.role);
}

export function clearSession() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
}
