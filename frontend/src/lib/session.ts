import type { Role } from "./roles";
import { isRole } from "./roles";

const ACCESS_TOKEN_KEY = "mediflow.accessToken";
const REFRESH_TOKEN_KEY = "mediflow.refreshToken";
const SESSION_KEY = "mediflow.sessionId";
const ROLE_KEY = "mediflow.role";
export const AUTH_CHANGE_EVENT = "mediflow:auth-change";

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  role: Role;
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

/** One login identity; rotating the access token must not reset mounted forms. */
export function getSessionKey(): string | null {
  if (!getToken()) return null;
  return localStorage.getItem(SESSION_KEY) ?? getToken();
}

export function mustChangePassword(): boolean {
  const token = getToken();
  if (!token) return false;
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(payload)).mustChangePassword === true;
  } catch { return false; }
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function renewAccessToken(expectedSession: string, accessToken: string): boolean {
  if (getSessionKey() !== expectedSession) return false;
  localStorage.setItem(SESSION_KEY, expectedSession);
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  notifySessionChanged();
  return true;
}

export function getRole(): Role | null {
  if (typeof window === "undefined") return null;
  const role = localStorage.getItem(ROLE_KEY);
  return role && isRole(role) ? role : null;
}

export function storeSession(session: AuthSession) {
  localStorage.setItem(SESSION_KEY, crypto.randomUUID());
  localStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
  localStorage.setItem(ROLE_KEY, session.role);
  notifySessionChanged();
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY);
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
  notifySessionChanged();
}

/**
 * Notify same-tab consumers when the session changes.
 * The native storage event only fires in other tabs, so login/logout in the
 * current tab would otherwise leave auth guards with a stale snapshot.
 */
function notifySessionChanged() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(AUTH_CHANGE_EVENT));
  }
}

export function subscribeToAuthChanges(onStoreChange: () => void) {
  if (typeof window === "undefined") return () => undefined;

  window.addEventListener(AUTH_CHANGE_EVENT, onStoreChange);
  window.addEventListener("storage", onStoreChange);

  return () => {
    window.removeEventListener(AUTH_CHANGE_EVENT, onStoreChange);
    window.removeEventListener("storage", onStoreChange);
  };
}
