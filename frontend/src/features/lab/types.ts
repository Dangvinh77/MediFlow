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
export interface PatientLabResultDTO {
  testId: string;
  recordId: string;
  labType: string;
  requestedDate: string;
  performedDate: string;
  conclusion: string | null;
  results: LabResultDTO[];
}
