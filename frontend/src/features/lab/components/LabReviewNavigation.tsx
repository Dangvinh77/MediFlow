"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useSyncExternalStore } from "react";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";

const getServerRole = () => null;

export function LabReviewNavigation() {
  const { localize } = useLocale();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  if (role !== "ADMIN" && role !== "LAB_TECH" && role !== "DOCTOR") return null;

  return <nav aria-label={localize("Quy trình xét nghiệm")} className="flex flex-wrap gap-2">
    <L10nValue>{role === "ADMIN" || role === "LAB_TECH" ? <Link href="/lab/worklist" className="inline-flex min-h-11 items-center rounded-lg border border-border bg-surface px-4 py-2 text-sm font-medium hover:bg-surface-muted"><L10n>{"Hàng đợi duyệt"}</L10n></Link> : null}</L10nValue>
    <L10nValue>{role === "DOCTOR" || role === "LAB_TECH" ? <Link href="/lab/critical" className="inline-flex min-h-11 items-center rounded-lg border border-border bg-surface px-4 py-2 text-sm font-medium hover:bg-surface-muted"><L10n>{"Cảnh báo nguy cấp"}</L10n></Link> : null}</L10nValue>
  </nav>;
}
