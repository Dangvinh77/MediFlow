export type LabTestStatus =
  | "PENDING"
  | "AWAITING_PAYMENT"
  | "READY"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";
export type LabCareEpisodeType = "OUTPATIENT_VISIT" | "ADMISSION";
export type LabCatalogGroup = "HEMATOLOGY" | "BIOCHEMISTRY" | "IMMUNOLOGY" | "URINALYSIS" | "MICROBIOLOGY";

export interface LabCatalogItemDTO {
  serviceCode: string;
  name: string;
  displayGroup: string;
  specimenType: string;
  preparation: string | null;
  turnaroundHours: number | null;
  priceCode: string;
  templateCode: string;
  panel: boolean;
}

export interface LabReferenceRangeDTO {
  sex: string | null;
  ageMinDays: number | null;
  ageMaxDays: number | null;
  low: number | string | null;
  high: number | string | null;
  criticalLow: number | string | null;
  criticalHigh: number | string | null;
  reviewed: boolean;
}

export type LabTemplateValueType = "QUANTITATIVE" | "QUALITATIVE" | "SEMI_QUANTITATIVE" | "TEXT";

export interface LabTemplateFieldDTO {
  fieldCode: string;
  label: string;
  position: number;
  required: boolean;
  valueType: LabTemplateValueType;
  unit: string | null;
  allowedOptions: string[] | null;
  allowsOperator: boolean;
  ranges: LabReferenceRangeDTO[];
}

export interface LabResultTemplateDTO {
  templateCode: string;
  version: number;
  name: string;
  fields: LabTemplateFieldDTO[];
}

export interface LabTestTemplateDTO {
  testId: string;
  serviceCode: string | null;
  template: LabResultTemplateDTO | null;
}

export interface LabResultDTO {
  resultId: string;
  indicator: string;
  value: string;
  unit: string | null;
  referenceRange: string | null;
}

export interface LabTestDTO {
  testId: string;
  recordId: string;
  patientId: string;
  requestingDepartmentId: string;
  labType: string;
  requestedDate: string;
  performedDate: string | null;
  status: LabTestStatus;
  conclusion: string | null;
  paid: boolean;
  results: LabResultDTO[] | null;
  createdAt: string;
  updatedAt: string;
  careContractVersion: number;
  sourceOrderId: string | null;
  careEpisodeType: LabCareEpisodeType | null;
  careEpisodeId: string | null;
  priceCode: string | null;
  clearanceId: string | null;
  emergencyOverrideId: string | null;
  resultVersion: number;
}

export interface CreateLabRequest {
  recordId: string;
  patientId: string;
  requestingDepartmentId: string;
  labType: string;
  requestedDate: string;
  sourceOrderId?: string | null;
  careEpisodeType?: LabCareEpisodeType | null;
  careEpisodeId?: string | null;
  priceCode?: string | null;
  serviceCode?: string | null;
}

export interface EmergencyOverrideRequest {
  overrideId: string;
  approvedBy: string;
  approverRole: string;
  reason: string;
  approvedAt: string;
}

export interface StartLabTestRequest {
  emergencyOverride: EmergencyOverrideRequest | null;
}

export interface LabResultInput {
  indicator: string;
  value: string;
  unit: string | null;
  referenceRange: string | null;
}

export interface AddLabResultsRequest {
  results: LabResultInput[];
  conclusion: string | null;
  performedDate: string;
}

export interface CancelLabTestRequest {
  reason: string;
  cancelledBy?: string | null;
}

export type LabReportStatus = "DRAFT" | "PENDING_REVIEW" | "RELEASED";
export type LabResultFlag = "LOW" | "NORMAL" | "HIGH" | "CRITICAL_LOW" | "CRITICAL_HIGH" | "UNDETERMINED";

export interface LabReportValueDTO {
  fieldCode: string;
  operator: string | null;
  value: string | null;
  noResultReason: string | null;
  flag: LabResultFlag | null;
}

export interface LabReportDTO {
  reportId: string;
  testId: string;
  resultVersion: number;
  status: LabReportStatus;
  draftRevision: number;
  templateCode: string;
  templateVersion: number;
  values: LabReportValueDTO[];
  conclusion: string | null;
  performedDate: string | null;
  amendmentReason: string | null;
  enteredBy: string | null;
  submittedBy: string | null;
  submittedAt: string | null;
  returnedBy: string | null;
  returnReason: string | null;
  releasedBy: string | null;
  releasedAt: string | null;
}

export interface SaveLabReportDraftRequest {
  draftRevision: number;
  values: Array<{
    fieldCode: string;
    operator?: string;
    value?: string;
    noResultReason?: string;
  }>;
  conclusion?: string;
  performedDate?: string;
}

export interface SubmitLabReportRequest {
  draftRevision: number;
}

export interface ReturnLabReportRequest {
  reason: string;
}

export interface AmendLabReportRequest {
  reason: string;
}

export interface LabSpecimenDTO {
  specimenId: string;
  accession: string;
  patientId: string;
  specimenType: string;
  testIds: string[];
  replacesSpecimenId: string | null;
  collectedBy: string | null;
  collectedAt: string;
  receivedBy: string | null;
  receivedAt: string | null;
  decision: null | "ACCEPTED" | "REJECTED";
  rejectionReason: string | null;
}

export interface CollectLabSpecimenRequest {
  specimenType: string;
  additionalTestIds?: string[];
  replacesSpecimenId?: string;
}

export interface LabSpecimenDecisionRequest {
  decision: "ACCEPTED" | "REJECTED";
  reason?: string;
}

export interface LabWorklistItemDTO {
  testId: string;
  patientId: string;
  labType: string;
  testStatus: string;
  serviceCode: string | null;
  serviceName: string | null;
  displayGroup: string;
  specimenStatus: "NOT_COLLECTED" | "COLLECTED" | "ACCEPTED" | "REJECTED";
  reportStatus: LabReportStatus | null;
  reportVersion: number | null;
  dueAt: string | null;
  overdue: boolean;
}

export interface LabCriticalNoticeDTO {
  noticeId: string;
  testId: string;
  resultVersion: number;
  fieldCode: string;
  label: string;
  value: string;
  unit: string | null;
  flag: LabResultFlag;
  patientId: string;
  departmentId: string;
  status: "OPEN" | "ACKNOWLEDGED";
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  createdAt: string;
}

export interface PatientLabResultDTO {
  testId: string;
  recordId: string;
  labType: string;
  requestedDate: string;
  performedDate: string;
  conclusion: string | null;
  results: LabResultDTO[];
}
