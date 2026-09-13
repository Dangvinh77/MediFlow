export type LabTestStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";

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
}
