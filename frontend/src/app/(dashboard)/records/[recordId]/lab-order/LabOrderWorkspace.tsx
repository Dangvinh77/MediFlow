"use client";

import { useState, useSyncExternalStore } from "react";
import { ClinicalLabOrderForm } from "@/features/medical-record/components/ClinicalLabOrderForm";
import type { ClinicalLabOrderDTO } from "@/features/medical-record/types";
import { CreateSourceLabRequest } from "@/features/lab/components/CreateSourceLabRequest";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/session";

// The route composes the two Vinh-owned contexts; neither feature imports the other.
export function LabOrderWorkspace({ recordId }: { recordId: string }) {
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);

  return <LabOrderWorkspaceContents key={`${recordId}:${role ?? "anonymous"}:${token ?? "anonymous"}`} recordId={recordId} />;
}

function LabOrderWorkspaceContents({ recordId }: { recordId: string }) {
  const [order, setOrder] = useState<ClinicalLabOrderDTO | null>(null);
  return <div className="mt-6 space-y-6">
    <p className="text-sm text-muted-foreground">Hoàn thành hai bước trên trang này. Nếu rời trang sau khi lập chỉ định, giữ mã chỉ định để đối soát; không lập lại cùng yêu cầu.</p>
    {!order ? <ClinicalLabOrderForm recordId={recordId} onCreated={setOrder} /> : <CreateSourceLabRequest key={order.sourceOrderId} request={{
      recordId: order.recordId,
      patientId: order.patientId,
      requestingDepartmentId: order.requestingDepartmentId,
      labType: order.labType,
      requestedDate: order.requestedDate,
      sourceOrderId: order.sourceOrderId,
      careEpisodeType: order.careEpisodeType,
      careEpisodeId: order.careEpisodeId,
      priceCode: order.priceCode,
    }} />}
  </div>;
}
