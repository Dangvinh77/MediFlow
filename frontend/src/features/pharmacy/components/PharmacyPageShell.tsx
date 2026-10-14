import type { ComponentProps } from "react";
import { PageShell } from "@/components/layout/PageShell";
import { PharmacyNav } from "./PharmacyNav";

/** Context composition only; the dashboard owns auth, header and theme. */
export function PharmacyPageShell({ children, ...props }: ComponentProps<typeof PageShell>) {
  return <PageShell {...props}><PharmacyNav />{children}</PageShell>;
}
