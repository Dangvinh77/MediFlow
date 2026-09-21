import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { NotificationLookup } from "@/features/notification/components/NotificationLookup";

export const metadata: Metadata = {
  title: "Thông báo | MediFlow",
};

export default function NotificationsPage() {
  return (
    <PageShell
      title="Thông báo"
      description="Tra cứu thông báo theo mã bệnh nhân."
    >
      <RoleGate allowed={["ADMIN", "NURSE", "PATIENT"]}>
        <NotificationLookup />
      </RoleGate>
    </PageShell>
  );
}
