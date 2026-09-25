import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { OutboxReplayForm } from "@/features/pharmacy/components/outbox/OutboxReplayForm";

export const metadata: Metadata = {
  title: "Outbox Pharmacy | MediFlow",
};

/** Route công cụ replay một outbox event theo eventId đã biết. */
export default function OutboxReplayPage() {
  return (
    <PageShell
      title="Outbox Pharmacy"
      description="Công cụ dành cho ADMIN để replay event theo mã đã biết."
    >
      <RoleGate allowed={["ADMIN"]}>
        <OutboxReplayForm />
      </RoleGate>
    </PageShell>
  );
}
