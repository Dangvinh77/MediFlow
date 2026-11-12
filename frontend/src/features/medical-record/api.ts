import { api, type MutationOptions } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  AddConsultationNoteRequest,
  AddCourseVisitRequest,
  AddDiagnosisRequest,
  AddPatientAllergyRequest,
  AmendmentAssignmentDTO,
  AssignAmendmentRequest,
  ChangeAllergyStatusRequest,
  ClinicalLabOrderDTO,
  CloseRecordRequest,
  CloseTreatmentCourseRequest,
  ConsultationNoteDTO,
  CompleteRecordRequest,
  CreateClinicalLabOrderRequest,
  CreateFollowUpRecommendationRequest,
  CreateMedicalRecordRequest,
  DecideAmendmentRequest,
  DiagnosisDTO,
  FollowUpRecommendationDTO,
  OpenTreatmentCourseRequest,
  PatientAllergiesDTO,
  PatientAllergyDTO,
  RecordAmendmentDTO,
  MedicalRecordDTO,
  RecordDocumentDTO,
  RecordHandoverDTO,
  RecordAccessDeclarationDTO,
  RecordAccessDeclarationRequest,
  RecordCapabilitiesDTO,
  RecordLabOrderDTO,
  RequestAmendmentRequest,
  RequestHandoverRequest,
  TimelineEntryDTO,
  TimelineQuery,
  TreatmentCourseDTO,
  UpdateMedicalRecordRequest,
  WithdrawLabOrderRequest,
} from "./types";

type V2MutationOptions = Required<Pick<MutationOptions, "idempotencyKey" | "correlationId">>;

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
    options: V2MutationOptions,
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
    options: V2MutationOptions,
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
    options: V2MutationOptions,
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
  timeline: (patientId: string, query: TimelineQuery = {}) => {
    const params = new URLSearchParams({
      page: String(query.page ?? 0),
      size: String(query.size ?? 20),
    });
    if (query.departmentId) params.set("departmentId", query.departmentId);
    if (query.status) params.set("status", query.status);
    return api.get<PageResult<TimelineEntryDTO>>(
      `/v2/patients/${encodeURIComponent(patientId)}/timeline?${params.toString()}`,
    );
  },
  documents: (recordId: string) =>
    api.get<RecordDocumentDTO[]>(`/v2/records/${encodeURIComponent(recordId)}/documents`),
  treatmentCourses: (patientId: string) =>
    api.get<TreatmentCourseDTO[]>(`/v2/patients/${encodeURIComponent(patientId)}/treatment-courses`),
  openTreatmentCourse: (body: OpenTreatmentCourseRequest, options: V2MutationOptions) =>
    api.post<TreatmentCourseDTO>("/v2/treatment-courses", body, options),
  addCourseVisit: (courseId: string, body: AddCourseVisitRequest, options: V2MutationOptions) =>
    api.post<TreatmentCourseDTO>(`/v2/treatment-courses/${encodeURIComponent(courseId)}/visits`, body, options),
  closeTreatmentCourse: (courseId: string, body: CloseTreatmentCourseRequest, options: V2MutationOptions) =>
    api.put<TreatmentCourseDTO>(`/v2/treatment-courses/${encodeURIComponent(courseId)}/closure`, body, options),
  handovers: (recordId: string) =>
    api.get<RecordHandoverDTO[]>(`/v2/records/${encodeURIComponent(recordId)}/handovers`),
  requestHandover: (recordId: string, body: RequestHandoverRequest, options: V2MutationOptions) =>
    api.post<RecordHandoverDTO>(`/v2/records/${encodeURIComponent(recordId)}/handovers`, body, options),
  acceptHandover: (recordId: string, handoverId: string, options: V2MutationOptions) =>
    api.put<RecordHandoverDTO>(
      `/v2/records/${encodeURIComponent(recordId)}/handovers/${encodeURIComponent(handoverId)}/acceptance`,
      undefined,
      options,
    ),
  consultationNotes: (recordId: string) =>
    api.get<ConsultationNoteDTO[]>(`/v2/records/${encodeURIComponent(recordId)}/consultation-notes`),
  addConsultationNote: (recordId: string, body: AddConsultationNoteRequest, options: V2MutationOptions) =>
    api.post<ConsultationNoteDTO>(`/v2/records/${encodeURIComponent(recordId)}/consultation-notes`, body, options),
  amendments: (recordId: string) =>
    api.get<RecordAmendmentDTO[]>(`/v2/records/${encodeURIComponent(recordId)}/amendments`),
  requestAmendment: (recordId: string, body: RequestAmendmentRequest, options: V2MutationOptions) =>
    api.post<RecordAmendmentDTO>(`/v2/records/${encodeURIComponent(recordId)}/amendments`, body, options),
  decideAmendment: (recordId: string, amendmentId: string, body: DecideAmendmentRequest, options: V2MutationOptions) =>
    api.put<RecordAmendmentDTO>(
      `/v2/records/${encodeURIComponent(recordId)}/amendments/${encodeURIComponent(amendmentId)}/decision`,
      body,
      options,
    ),
  assignAmendment: (recordId: string, body: AssignAmendmentRequest, options: V2MutationOptions) =>
    api.post<AmendmentAssignmentDTO>(`/v2/records/${encodeURIComponent(recordId)}/amendment-assignments`, body, options),
  pendingAmendments: () => api.get<RecordAmendmentDTO[]>("/v2/amendments/pending"),
  allergies: (patientId: string) =>
    api.get<PatientAllergiesDTO>(`/v2/patients/${encodeURIComponent(patientId)}/allergies`),
  addAllergy: (patientId: string, body: AddPatientAllergyRequest, options: V2MutationOptions) =>
    api.post<PatientAllergyDTO>(`/v2/patients/${encodeURIComponent(patientId)}/allergies`, body, options),
  changeAllergyStatus: (patientId: string, allergyId: string, body: ChangeAllergyStatusRequest, options: V2MutationOptions) =>
    api.put<PatientAllergyDTO>(
      `/v2/patients/${encodeURIComponent(patientId)}/allergies/${encodeURIComponent(allergyId)}/status`,
      body,
      options,
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
  update: (recordId: string, body: UpdateMedicalRecordRequest, options: V2MutationOptions) =>
    api.put<MedicalRecordDTO>(
      `/v1/records/${encodeURIComponent(recordId)}`,
      body,
      options,
    ),
  addDiagnosis: (recordId: string, body: AddDiagnosisRequest) =>
    api.post<DiagnosisDTO>(
      `/v1/records/${encodeURIComponent(recordId)}/diagnoses`,
      body,
    ),
};
