"use client";

import Link from "next/link";
import { useSyncExternalStore } from "react";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";

const getServerRole = () => null;

export function OrganizationAdminActions() {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  if (role !== "ADMIN") return null;
  const linkClass = "inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";
  return <div className="flex flex-wrap items-center gap-2"><Link href="/organization/departments/new" className={linkClass}>Thêm khoa</Link><Link href="/organization/staff/new" className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Thêm nhân sự</Link><Link href="/organization/accounts" className={linkClass}>Quản lý tài khoản</Link></div>;
}
