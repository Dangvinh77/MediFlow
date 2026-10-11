import Link from "next/link";
import type { Role } from "@/lib/roles";

const createRoles: readonly Role[] = ["ADMIN", "DOCTOR"];

export function AdmissionCreateLink({ role }: { role: Role | null }) {
  if (role === null || !createRoles.includes(role)) return null;

  return (
    <Link
      href="/inpatient/new"
      className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
    >
      Tạo đợt nội trú
    </Link>
  );
}
