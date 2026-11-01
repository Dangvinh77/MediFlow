"use client";
import { useSyncExternalStore } from "react";
import { api } from "./api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "./session";
import type { Role } from "./roles";

export interface ManagementDepartment { departmentId: string; departmentName: string; appointmentId: string | null; revision: number }
export interface OwnCapabilities { accountId: string; staffId: string | null; role: Role; canPracticeAsDoctor: boolean; managementDepartments: ManagementDepartment[]; observedAt: string }
interface Snapshot { session: string | null; data: OwnCapabilities | null; loading: boolean; error: string | null }
const empty: Snapshot = { session: null, data: null, loading: false, error: null };
let snapshot = empty;
let generation = 0;
const listeners = new Set<() => void>();
function publish(value: Snapshot) { snapshot = value; listeners.forEach(listener => listener()); }
function syncSession() { if (snapshot.session !== getSessionKey()) { generation++; publish({ ...empty, session: getSessionKey() }); } }
function subscribe(listener: () => void) {
  listeners.add(listener);
  const unsubscribe = subscribeToAuthChanges(syncSession);
  syncSession();
  return () => { listeners.delete(listener); unsubscribe(); };
}
export async function refreshCapabilities() {
  syncSession();
  const session = getSessionKey();
  const request = ++generation;
  if (!session) return;
  publish({ session, data: snapshot.data, loading: true, error: null });
  try {
    const data = await api.get<OwnCapabilities>("/v1/org/me/capabilities");
    if (data.role !== getRole()) throw new Error("Quyền nhận được không khớp với phiên đăng nhập.");
    if (getSessionKey() === session && generation === request) publish({ session, data, loading: false, error: null });
  } catch (cause) {
    if (getSessionKey() === session && generation === request) publish({ session, data: null, loading: false, error: cause instanceof Error ? cause.message : "Không thể kiểm tra quyền quản lý khoa." });
  }
}
export function useCapabilities() { return useSyncExternalStore(subscribe, () => snapshot, () => empty); }
