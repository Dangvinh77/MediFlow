export interface DiagnosisDTO {
  diagnosisId: string;
  diagnosisName: string;
  description: string | null;
  icdCode: string | null;
}

export type MedicalRecordStatus = "OPEN" | "COMPLETED";

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
  symptoms: string;
  appointmentId: string | null;
  diagnoses: DiagnosisDTO[];
  createdAt: string;
  updatedAt: string;
  status: MedicalRecordStatus;
  disposition: RecordDisposition | null;
  dispositionNote: string | null;
  completedAt: string | null;
}
