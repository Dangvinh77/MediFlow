import type { ReactNode } from "react";
import DashboardShell from "./DashboardShell";

export const dynamic = "force-dynamic";

export default function DashboardLayout({ children }: { children: ReactNode }) {
  return (
    <DashboardShell schedulingEnabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"}>
      {children}
    </DashboardShell>
  );
}
