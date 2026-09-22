import type { Role } from "./roles";
import { isRole } from "./roles";

const ACCESS_TOKEN_KEY = "mediflow.accessToken";
const REFRESH_TOKEN_KEY = "mediflow.refreshToken";
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

export function getRole(): Role | null {
  if (typeof window === "undefined") return null;
  const role = localStorage.getItem(ROLE_KEY);
  return role && isRole(role) ? role : null;
}

export function storeSession(session: AuthSession) {
  localStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
  localStorage.setItem(ROLE_KEY, session.role);
  notifySessionChanged();
}

export function clearSession() {
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
