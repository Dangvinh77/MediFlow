import { api } from "@/lib/api";
import type { MedicalRecordDTO } from "./types";

export const medicalRecordApi = {
  getById: (recordId: string) =>
    api.get<MedicalRecordDTO>(
      `/v1/records/${encodeURIComponent(recordId)}`,
    ),
  byPatient: (patientId: string) =>
    api.get<MedicalRecordDTO[]>(`/v1/records/patient/${encodeURIComponent(patientId)}`),
};
