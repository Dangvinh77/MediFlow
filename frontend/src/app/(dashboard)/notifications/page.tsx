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
      description="Xem thông báo cá nhân hoặc tra cứu lịch sử thông báo theo quyền của bạn."
    >
      <RoleGate allowed={["ADMIN", "NURSE", "PATIENT"]}>
        <NotificationLookup />
      </RoleGate>
    </PageShell>
  );
}
