import { api } from "@/lib/api";
import type {
  AddDiagnosisRequest,
  ClinicalLabOrderDTO,
  CompleteRecordRequest,
  CreateClinicalLabOrderRequest,
  CreateMedicalRecordRequest,
  DiagnosisDTO,
  MedicalRecordDTO,
  UpdateMedicalRecordRequest,
} from "./types";

export const medicalRecordApi = {
  // Contract preparation only; V1 controls require integrated runtime acceptance.
  complete: (recordId: string, body: CompleteRecordRequest) =>
    api.put<MedicalRecordDTO>(
      `/v1/records/${encodeURIComponent(recordId)}/complete`,
      body,
    ),
  createLabOrder: (recordId: string, body: CreateClinicalLabOrderRequest) =>
    api.post<ClinicalLabOrderDTO>(
      `/v1/records/${encodeURIComponent(recordId)}/lab-orders`,
      body,
    ),
  getById: (recordId: string) =>
    api.get<MedicalRecordDTO>(
      `/v1/records/${encodeURIComponent(recordId)}`,
    ),
  byPatient: (patientId: string) =>
    api.get<MedicalRecordDTO[]>(`/v1/records/patient/${encodeURIComponent(patientId)}`),
  create: (body: CreateMedicalRecordRequest) =>
    api.post<MedicalRecordDTO>("/v1/records", body),
  update: (recordId: string, body: UpdateMedicalRecordRequest) =>
    api.put<MedicalRecordDTO>(
      `/v1/records/${encodeURIComponent(recordId)}`,
      body,
    ),
  addDiagnosis: (recordId: string, body: AddDiagnosisRequest) =>
    api.post<DiagnosisDTO>(
      `/v1/records/${encodeURIComponent(recordId)}/diagnoses`,
      body,
    ),
};
