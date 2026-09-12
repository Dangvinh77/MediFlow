import { api } from "@/lib/api";
import type { MedicalRecordDTO } from "./types";

export const medicalRecordApi = {
  byPatient: (patientId: string) =>
    api.get<MedicalRecordDTO[]>(`/v1/records/patient/${encodeURIComponent(patientId)}`),
};
