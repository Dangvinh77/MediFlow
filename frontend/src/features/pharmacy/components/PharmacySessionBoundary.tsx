"use client";

import { Fragment, useCallback, useEffect, useRef, useSyncExternalStore, type ReactNode } from "react";
import { getRole } from "@/lib/auth";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import { getPharmacyCapabilities, type PharmacyCapabilities } from "../permissions";
import { PharmacyIdentityProvider } from "./PharmacyIdentity";

/** Session-local drafts/results must never move between two accounts with the same role. */
export function PharmacySessionBoundary({ children, capability }: { children: ReactNode; capability?: keyof PharmacyCapabilities }) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  if (capability && !getPharmacyCapabilities(role)[capability]) return <p role="alert" className="mt-6 text-sm text-danger">Tài khoản hiện tại không có quyền xem dữ liệu thuốc.</p>;
  return <Fragment key={`${role}:${token}`}><PharmacyIdentityProvider>{children}</PharmacyIdentityProvider></Fragment>;
}

export function usePharmacyRequestScope(capability: keyof PharmacyCapabilities) {
  const session = useRef({ token: getSessionKey(), role: getRole() });
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  return useCallback(() => mounted.current && getSessionKey() === session.current.token
    && getRole() === session.current.role && getPharmacyCapabilities(getRole())[capability], [capability]);
}
