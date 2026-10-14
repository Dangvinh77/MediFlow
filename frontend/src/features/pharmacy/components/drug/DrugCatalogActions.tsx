"use client";

import Link from "next/link";
import { useSyncExternalStore } from "react";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { getPharmacyCapabilities } from "../../permissions";

export function DrugCatalogActions() {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  if (!getPharmacyCapabilities(role).canCreateDrug) return null;
  return (
    <Link href="/pharmacy/drugs/new" className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-primary bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
      <span aria-hidden="true">+</span> Thêm thuốc
    </Link>
  );
}
