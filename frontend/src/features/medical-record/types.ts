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
  symptoms: string | null;
  appointmentId: string | null;
  diagnoses: DiagnosisDTO[];
  createdAt: string;
  updatedAt: string | null;
  status: MedicalRecordStatus;
  disposition: RecordDisposition | null;
  dispositionNote: string | null;
  completedAt: string | null;
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
