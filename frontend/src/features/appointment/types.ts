export type AppointmentStatus =
  | "PENDING"
  | "ARRIVED"
  | "AWAITING_PAYMENT"
  | "READY_FOR_EXAM"
  | "IN_EXAM"
  | "COMPLETED"
  | "CANCELLED";

export type LegacyAppointmentStatus = "ARRIVED" | "CANCELLED";

export interface AppointmentDTO {
  appointmentId: string;
  patientId: string;
  doctorId: string;
  departmentId: string;
  appointmentDate: string;
  appointmentTime: string;
  status: AppointmentStatus;
  reason: string | null;
  createdAt: string;
  updatedAt: string | null;
  careContractVersion: number;
  examClearanceId: string | null;
  examClearanceAt: string | null;
  emergencyOverrideId: string | null;
  examPriceCode: string | null;
  checkedInAt: string | null;
  examinationStartedAt: string | null;
  completedAt: string | null;
}

export interface CreateAppointmentRequest {
  patientId: string;
  doctorId: string;
  departmentId: string;
  appointmentDate: string;
  appointmentTime: string;
  reason: string | null;
}

export interface UpdateAppointmentRequest {
  appointmentDate: string;
  appointmentTime: string;
  reason: string | null;
}

export interface ChangeAppointmentStatusRequest {
  status: LegacyAppointmentStatus;
}
