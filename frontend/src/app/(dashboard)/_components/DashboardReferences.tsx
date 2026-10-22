"use client";
import { useMemo, type ReactNode } from "react";
import { ReferenceProvider } from "@/components/references/ReferenceProvider";
import type { Role } from "@/lib/roles";
import { createReferenceDirectory } from "./reference-directory";

export function DashboardReferences({ role, children }: { role: Role | null; children: ReactNode }) {
  const directory = useMemo(() => createReferenceDirectory(role), [role]);
  return <ReferenceProvider directory={directory}>{children}</ReferenceProvider>;
}
