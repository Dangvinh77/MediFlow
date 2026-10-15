import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { OutboxReplayForm } from "@/features/billing/components/OutboxReplayForm";

export const metadata: Metadata = {
  title: "Phát lại outbox Billing | MediFlow",
};

export default function BillingOutboxAdminPage() {
  return (
    <PageShell title="Phát lại outbox Billing" description="Công cụ vận hành: phát lại một event đang bị giữ lại.">
      <RoleGate allowed={["ADMIN"]}>
        <OutboxReplayForm />
      </RoleGate>
    </PageShell>
  );
}
