import { api, type MutationOptions } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  AddDiagnosisRequest,
  ClinicalLabOrderDTO,
  CloseRecordRequest,
  CompleteRecordRequest,
  CreateClinicalLabOrderRequest,
  CreateFollowUpRecommendationRequest,
  CreateMedicalRecordRequest,
  DiagnosisDTO,
  FollowUpRecommendationDTO,
  MedicalRecordDTO,
  RecordAccessDeclarationDTO,
  RecordAccessDeclarationRequest,
  RecordCapabilitiesDTO,
  RecordLabOrderDTO,
  UpdateMedicalRecordRequest,
  WithdrawLabOrderRequest,
} from "./types";

export const medicalRecordApi = {
  mine: (page = 0) => api.get<PageResult<import("./types").PatientExamResultDTO>>(`/v1/records/mine?page=${page}&size=20`),
  // Commands are exposed only by the server-controlled outpatient UI gate.
  complete: (recordId: string, body: CompleteRecordRequest) =>
    api.put<MedicalRecordDTO>(
      `/v1/records/${encodeURIComponent(recordId)}/complete`,
      body,
    ),
  closeWithoutConclusion: (
    recordId: string,
    body: CloseRecordRequest,
    options: Required<Pick<MutationOptions, "idempotencyKey" | "correlationId">>,
  ) =>
    api.put<MedicalRecordDTO>(
      `/v2/records/${encodeURIComponent(recordId)}/close-without-conclusion`,
      body,
      options,
    ),
  labOrders: (recordId: string) =>
    api.get<RecordLabOrderDTO[]>(`/v2/records/${encodeURIComponent(recordId)}/lab-orders`),
  withdrawLabOrder: (
    recordId: string,
    sourceOrderId: string,
    body: WithdrawLabOrderRequest,
    options: Required<Pick<MutationOptions, "idempotencyKey" | "correlationId">>,
  ) =>
    api.put<RecordLabOrderDTO>(
      `/v2/records/${encodeURIComponent(recordId)}/lab-orders/${encodeURIComponent(sourceOrderId)}/withdrawal`,
      body,
      options,
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
  capabilities: (recordId: string) =>
    api.get<RecordCapabilitiesDTO>(
      `/v2/records/${encodeURIComponent(recordId)}/capabilities`,
    ),
  declareAccess: (body: RecordAccessDeclarationRequest) =>
    api.post<RecordAccessDeclarationDTO>("/v2/records/access-declarations", body),
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
