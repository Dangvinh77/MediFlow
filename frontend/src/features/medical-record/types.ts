export interface DiagnosisDTO {
  diagnosisId: string;
  diagnosisName: string;
  description: string | null;
  icdCode: string | null;
}

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
}
