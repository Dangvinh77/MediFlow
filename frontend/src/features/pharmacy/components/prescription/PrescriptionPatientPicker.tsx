"use client";

import { useState } from "react";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReferenceProvider, type ReferenceDirectory } from "@/components/references/ReferenceProvider";
import { controlClassName } from "@/components/ui/Field";
import { formatLocalDate } from "@/lib/format";
import { pharmacyApi } from "../../api";
import { usePharmacyRequestScope } from "../PharmacySessionBoundary";

export function PrescriptionPatientPicker({ value, onChange, disabled }: {
  value: string; onChange: (id: string) => void; disabled?: boolean;
}) {
  const isCurrent = usePharmacyRequestScope("canReadPrescription");
  const [directory] = useState<ReferenceDirectory>(() => ({
    canSearch: kind => kind === "patient",
    async search(_kind, keyword, page) {
      const result = await pharmacyApi.searchPatients(keyword, page);
      if (!isCurrent()) throw new Error("Phiên tra cứu đã thay đổi");
      return { items: result.content.map(patient => ({ id: patient.maBenhNhan,
        label: `${patient.hoTen} · ${formatLocalDate(patient.ngaySinh)}` })),
      hasMore: result.number + 1 < result.totalPages };
    },
    async resolve(_kind, id) {
      const patient = await pharmacyApi.getPatientIdentity(id);
      if (!isCurrent() || patient.maBenhNhan !== id) return null;
      return `${patient.hoTen} · ${formatLocalDate(patient.ngaySinh)}`;
    },
  }));
  return <ReferenceProvider directory={directory}>
    <ReferenceInput id="prescription-patient" kind="patient" className={controlClassName}
      value={value} onValueChange={onChange} placeholder="Nhập tên bệnh nhân rồi chọn đúng người" disabled={disabled} />
  </ReferenceProvider>;
}
