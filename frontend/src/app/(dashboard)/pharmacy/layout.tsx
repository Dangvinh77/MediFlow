import type { ReactNode } from "react";

/** Each page composes its local navigation through PharmacyPageShell. */
export default function PharmacyLayout({ children }: Readonly<{ children: ReactNode }>) {
  return children;
}
