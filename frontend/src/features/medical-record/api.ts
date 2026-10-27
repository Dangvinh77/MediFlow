import { api, type MutationOptions } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  AddDiagnosisRequest,
  ClinicalLabOrderDTO,
  CompleteRecordRequest,
  CreateClinicalLabOrderRequest,
  CreateFollowUpRecommendationRequest,
  CreateMedicalRecordRequest,
  DiagnosisDTO,
  FollowUpRecommendationDTO,
  MedicalRecordDTO,
  UpdateMedicalRecordRequest,
} from "./types";

export const medicalRecordApi = {
  mine: (page = 0) => api.get<PageResult<import("./types").PatientExamResultDTO>>(`/v1/records/mine?page=${page}&size=20`),
  // Commands are exposed only by the server-controlled outpatient UI gate.
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
  getFollowUpRecommendation: (recordId: string) =>
    api.get<FollowUpRecommendationDTO>(
      `/v2/records/${encodeURIComponent(recordId)}/follow-up-recommendation`,
    ),
  createFollowUpRecommendation: (
    recordId: string,
    body: CreateFollowUpRecommendationRequest,
    options: Required<Pick<MutationOptions, "idempotencyKey" | "correlationId">>,
  ) =>
    api.post<FollowUpRecommendationDTO>(
      `/v2/records/${encodeURIComponent(recordId)}/follow-up-recommendations`,
      body,
      options,
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
