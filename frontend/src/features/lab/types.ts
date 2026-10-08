export type LabTestStatus =
  | "PENDING"
  | "AWAITING_PAYMENT"
  | "READY"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";
export type LabCareEpisodeType = "OUTPATIENT_VISIT" | "ADMISSION";

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
}

export interface StartLabTestRequest {
  emergencyOverride: null;
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
  cancelledBy: string;
}
