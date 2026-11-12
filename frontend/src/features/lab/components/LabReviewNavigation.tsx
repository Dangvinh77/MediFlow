"use client";

import Link from "next/link";
import { useSyncExternalStore } from "react";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";

const getServerRole = () => null;

export function LabReviewNavigation() {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  if (role !== "ADMIN" && role !== "LAB_TECH" && role !== "DOCTOR") return null;

  return <nav aria-label="Quy trình xét nghiệm" className="flex flex-wrap gap-2">
    {role === "ADMIN" || role === "LAB_TECH" ? <Link href="/lab/worklist" className="inline-flex min-h-11 items-center rounded-lg border border-border bg-surface px-4 py-2 text-sm font-medium hover:bg-surface-muted">Hàng đợi duyệt</Link> : null}
    {role === "DOCTOR" || role === "LAB_TECH" ? <Link href="/lab/critical" className="inline-flex min-h-11 items-center rounded-lg border border-border bg-surface px-4 py-2 text-sm font-medium hover:bg-surface-muted">Cảnh báo nguy cấp</Link> : null}
  </nav>;
}
