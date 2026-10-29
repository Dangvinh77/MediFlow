import { outpatientUiEnabled } from "@/lib/outpatient-ui";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { LabTable } from "@/features/lab/components/LabTable";

export const metadata: Metadata = { title: "Xét nghiệm | MediFlow" };

export const dynamic = "force-dynamic";

export default async function LabPage({ searchParams }: { searchParams: Promise<{ episodeId?: string | string[] }> }) {
  const params = await searchParams;
  const episodeId = typeof params.episodeId === "string" ? params.episodeId : "";
  return (
    <PageShell
      title="Xét nghiệm"
      description="Hàng đợi xét nghiệm theo khoa, trạng thái và đợt chăm sóc."
    >
      <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE", "LAB_TECH"]}>
        <LabTable key={episodeId} initialEpisodeId={episodeId} outpatientEnabled={outpatientUiEnabled()} />
      </RoleGate>
    </PageShell>
  );
}
