export interface DiagnosisDTO {
  diagnosisId: string;
  diagnosisName: string;
  description: string | null;
  icdCode: string | null;
}

export type MedicalRecordStatus = "OPEN" | "COMPLETED" | "CLOSED_WITHOUT_CONCLUSION";

export type RecordClosureReason = "PATIENT_LEFT" | "PATIENT_REFUSED_EXAM" | "PATIENT_REFUSED_ORDER" | "OTHER";

export interface CloseRecordRequest {
  reason: RecordClosureReason;
  note: string;
}

export interface RecordLabOrderDTO {
  sourceOrderId: string;
  testId: string | null;
  labType: string;
  status: string;
  orderedAt: string;
}

export type RecordDisposition =
  | "OUTPATIENT_FOLLOW_UP"
  | "PRESCRIPTION"
  | "ADMISSION"
  | "TRANSFER"
  | "OTHER";

export interface MedicalRecordDTO {
  recordId: string;
  patientId: string;
  doctorId: string;
  departmentId: string;
  examinationDate: string;
  symptoms: string | null;
  appointmentId: string | null;
  diagnoses: DiagnosisDTO[];
  createdAt: string;
  updatedAt: string | null;
  status: MedicalRecordStatus;
  disposition: RecordDisposition | null;
  dispositionNote: string | null;
  completedAt: string | null;
  previousRecordId?: string;
  closureReason?: RecordClosureReason;
  closureNote?: string;
  closedAt?: string;
}

export interface AddDiagnosisRequest {
  diagnosisName: string;
  description: string | null;
  icdCode: string | null;
}

export interface CreateMedicalRecordRequest {
  patientId: string;
  doctorId: string;
  departmentId: string;
  examinationDate: string;
  symptoms: string | null;
  appointmentId: string | null;
  diagnoses: AddDiagnosisRequest[];
}

export interface UpdateMedicalRecordRequest {
  symptoms: string | null;
}

export interface CompleteRecordRequest {
  disposition: RecordDisposition;
  dispositionNote: string | null;
}

export interface CreateClinicalLabOrderRequest {
  labType: string;
  requestedDate: string;
  priceCode: string;
}

export interface ClinicalLabOrderDTO {
  sourceOrderId: string;
  recordId: string;
  patientId: string;
  requestingDoctorId: string;
  requestingDepartmentId: string;
  careEpisodeType: "OUTPATIENT_VISIT" | "ADMISSION";
  careEpisodeId: string;
  labType: string;
  requestedDate: string;
  priceCode: string;
  orderedAt: string;
}

export interface FollowUpRecommendationDTO {
  recommendationId: string;
  sourceRecordId: string;
  sourceRegistrationId: string;
  patientId: string;
  departmentId: string;
  preferredDoctorId: string;
  recommendedFrom: string;
  recommendedThrough: string;
  reason: string;
  preparationInstructions: string | null;
  createdAt: string;
}

export interface CreateFollowUpRecommendationRequest {
  recommendedFrom: string;
  recommendedThrough: string;
  reason: string;
  preparationInstructions: string | null;
}
export interface PatientExamResultDTO {
  recordId: string;
  doctorId: string;
  departmentId: string;
  examinationDate: string;
  symptoms: string | null;
  diagnoses: DiagnosisDTO[];
  disposition: RecordDisposition | null;
  dispositionNote: string | null;
  completedAt: string;
}

export interface RecordCapabilitiesDTO {
  recordId: string;
  patientId: string;
  canRead: boolean;
  readBasis: string | null;
  canWriteContent: boolean;
  canComplete: boolean;
  canRequestAmendment: boolean;
  writeBlockedReason: string | null;
}

export type RecordAccessPurpose = "CONSULTATION" | "CARE_CONTINUITY" | "QUALITY_REVIEW" | "EMERGENCY" | "RECORD_MANAGEMENT";

export interface RecordAccessDeclarationRequest {
  patientId: string;
  purpose: RecordAccessPurpose;
  reason: string | null;
}

export interface RecordAccessDeclarationDTO {
  declarationId: string;
  patientId: string;
  purpose: RecordAccessPurpose;
  expiresAt: string;
}
