"use client";
import { RecordPicker } from "./RecordPicker";
import { patientApi } from "../api";
async function load(keyword: string, page: number) {
  const result = await patientApi.search({ keyword, page, size: 20 });
  return { items: result.content.map(patient => ({ id: patient.maBenhNhan, label: `${patient.hoTen} · ${patient.ngaySinh}` })), totalPages: result.totalPages };
}
export function PatientPicker({ value, onChange, disabled = false, id = "patientId" }: { value: string; onChange: (id: string) => void; disabled?: boolean; id?: string }) {
  return <RecordPicker id={id} label="Bệnh nhân" value={value} onChange={onChange} disabled={disabled} load={load} required />;
}
