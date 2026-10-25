import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PharmacyPageShell } from "@/features/pharmacy/components/PharmacyPageShell";
import { OutboxReplayForm } from "@/features/pharmacy/components/outbox/OutboxReplayForm";

export const metadata: Metadata = {
  title: "Khôi phục đồng bộ dữ liệu | MediFlow",
};

/** Route công cụ replay một outbox event theo eventId đã biết. */
export default function OutboxReplayPage() {
  return (
    <PharmacyPageShell
      title="Khôi phục đồng bộ dữ liệu"
      description="Công cụ bảo trì dành cho quản trị hệ thống. Chỉ sử dụng khi đã xác định được bản tin cần gửi lại."
    >
      <RoleGate allowed={["ADMIN"]}>
        <OutboxReplayForm />
      </RoleGate>
    </PharmacyPageShell>
  );
}
