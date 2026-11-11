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

/** Body for taking back an order Lab never received. */
export interface WithdrawLabOrderRequest {
  reason: string;
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

export interface ClinicalContentDTO {
  chiefComplaint?: string | null;
  presentIllness?: string | null;
  pastHistory?: string | null;
  allergyNote?: string | null;
  physicalExam?: string | null;
  advice?: string | null;
}

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
  responsibleDoctorId?: string;
  chiefComplaint?: string;
  presentIllness?: string;
  pastHistory?: string;
  allergyNote?: string;
  physicalExam?: string;
  advice?: string;
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
  symptoms?: string | null;
  chiefComplaint?: string | null;
  presentIllness?: string | null;
  pastHistory?: string | null;
  allergyNote?: string | null;
  physicalExam?: string | null;
  advice?: string | null;
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
  canHandover?: boolean;
  canAcceptHandover?: boolean;
  canAddConsultationNote?: boolean;
  canAmend?: boolean;
  canDecideAmendments?: boolean;
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

export type TimelineVisitKind = "FOLLOW_UP" | "RETURN" | "NEW";

export interface TimelineEntryDTO {
  recordId: string;
  examinationDate: string;
  departmentId: string;
  doctorId: string;
  responsibleDoctorId?: string | null;
  visitKind?: TimelineVisitKind | null;
  status: MedicalRecordStatus;
  disposition: RecordDisposition | null;
  previousRecordId?: string | null;
  treatmentCourseIds?: string[] | null;
}

export interface TimelineQuery {
  page?: number;
  size?: number;
  departmentId?: string | null;
  status?: MedicalRecordStatus | null;
}

export type RecordDocumentType = "LAB" | "PRESCRIPTION";

export interface RecordDocumentDTO {
  type: RecordDocumentType;
  referenceId: string;
  summary: string;
  attachedAt: string;
  afterConclusion: boolean;
}

export type TreatmentCourseStatus = "OPEN" | "CLOSED";
export type TreatmentCourseDecision = "RESOLVED" | "TRANSFERRED" | "PATIENT_STOPPED" | "OTHER";

export interface TreatmentCourseDTO {
  courseId: string;
  patientId: string;
  departmentId: string;
  problem: string;
  openingReason: string;
  responsibleDoctorId: string;
  status: TreatmentCourseStatus;
  recordIds: string[];
  openedAt: string;
  lastVisitDate: string;
  reviewDue: boolean;
  closureDecision?: TreatmentCourseDecision | null;
  closureNote?: string | null;
  closedAt?: string | null;
}

export interface OpenTreatmentCourseRequest {
  patientId: string;
  problem: string;
  openingReason: string;
  recordId: string;
}

export interface AddCourseVisitRequest {
  recordId: string;
}

export interface CloseTreatmentCourseRequest {
  decision: TreatmentCourseDecision;
  note: string;
}

export type RecordHandoverStatus = "PENDING" | "ACCEPTED";

export interface RecordHandoverDTO {
  handoverId: string;
  fromDoctorId: string;
  toDoctorId: string;
  reason: string;
  pendingItems: string;
  status: RecordHandoverStatus;
  requestedAt: string;
  acceptedAt?: string;
}

export interface RequestHandoverRequest {
  toDoctorId: string;
  reason: string;
  pendingItems: string;
}

export interface ConsultationNoteDTO {
  noteId: string;
  authorStaffId: string;
  opinion: string;
  createdAt: string;
}

export interface AddConsultationNoteRequest {
  opinion: string;
}

export type AmendmentSection =
  | "SYMPTOMS"
  | "CHIEF_COMPLAINT"
  | "PRESENT_ILLNESS"
  | "PAST_HISTORY"
  | "ALLERGY_NOTE"
  | "PHYSICAL_EXAM"
  | "ADVICE"
  | "DISPOSITION_NOTE"
  | "CLOSURE_NOTE"
  | "DIAGNOSIS";
export type RecordAmendmentStatus = "PENDING" | "APPROVED" | "REJECTED";
export type RecordAmendmentDecision = "APPROVED" | "REJECTED";

export interface RecordAmendmentDTO {
  amendmentId: string;
  section: AmendmentSection;
  originalText: string;
  amendedText: string;
  reason: string;
  authorStaffId: string;
  assignedByStaffId?: string;
  status: RecordAmendmentStatus;
  requestedAt: string;
  decidedByStaffId?: string;
  decisionNote?: string;
  decidedAt?: string;
  contentHash?: string;
}

export interface RequestAmendmentRequest {
  section: AmendmentSection;
  amendedText: string;
  reason: string;
}

export interface DecideAmendmentRequest {
  decision: RecordAmendmentDecision;
  note: string;
}

export interface AssignAmendmentRequest {
  assigneeStaffId: string;
  reason: string;
}

export interface AmendmentAssignmentDTO {
  assignmentId: string;
  recordId: string;
  assigneeStaffId: string;
  assignedByStaffId: string;
  reason: string;
  createdAt: string;
}

export type AllergySeverity = "MILD" | "MODERATE" | "SEVERE" | "UNKNOWN";
export type AllergyStatus = "ACTIVE" | "RESOLVED" | "ENTERED_IN_ERROR";

export interface PatientAllergyDTO {
  allergyId: string;
  substance: string;
  reaction?: string | null;
  severity: AllergySeverity;
  status: AllergyStatus;
  recordedByStaffId: string;
  recordedAt: string;
  sourceRecordId?: string | null;
  statusReason?: string | null;
  statusChangedByStaffId?: string | null;
  statusChangedAt?: string | null;
}

export interface PatientAllergiesDTO {
  active: PatientAllergyDTO[];
  inactive: PatientAllergyDTO[];
}

export interface AddPatientAllergyRequest {
  substance: string;
  reaction: string | null;
  severity: AllergySeverity;
  sourceRecordId: string | null;
}

export interface ChangeAllergyStatusRequest {
  status: AllergyStatus;
  reason: string;
}
