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

export interface EmergencyOverrideRequest {
  overrideId: string;
  approvedBy: string;
  approverRole: string;
  reason: string;
  approvedAt: string;
}

export interface StartExamRequest {
  emergencyOverride: EmergencyOverrideRequest | null;
}

// Outpatient scheduling V2 DTOs. These mirror docs/plans/contracts/outpatient-scheduling-v2.schema.json.
export interface ConsultationBreak {
  startTime: string;
  endTime: string;
}

export interface SchedulingScopeSnapshot {
  accountId: string;
  revision: number;
  departmentIds: string[];
  updatedAt: string | null;
  updatedByAccountId: string | null;
}

export interface SchedulingScopeCommand {
  expectedRevision: number;
  departmentIds: string[];
  reason: string;
}

export interface ConsultationRoomSummary {
  roomId: string;
  departmentId: string;
  roomName: string;
  roomType: string;
}

export type ConsultationSessionStatus = "OPEN" | "STOPPED" | "CANCELLED";

export interface ConsultationSessionCreate {
  doctorId: string;
  departmentId: string;
  roomId: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  breaks: ConsultationBreak[];
  capacity: number;
}

export interface ConsultationSessionUpdate extends ConsultationSessionCreate {
  expectedRevision: number;
  reason: string;
}

export interface ConsultationSessionStatusCommand {
  expectedRevision: number;
  status: "OPEN" | "STOPPED" | "CANCELLED";
  reason: string;
}

export interface ConsultationSessionDTO extends ConsultationSessionCreate {
  sessionId: string;
  revision: number;
  status: ConsultationSessionStatus;
  heldCount: number;
  paymentLockedCount: number;
  confirmedCount: number;
  consumedCount: number;
  availableCount: number;
  observedAt: string;
}

export interface DoctorCalendarDTO {
  doctorId: string;
  fromDate: string;
  toDate: string;
  timezone: "Asia/Ho_Chi_Minh";
  sessions: ConsultationSessionDTO[];
}

export type RegistrationStatus = "AWAITING_PAYMENT" | "CONFIRMED" | "IN_EXAM" | "COMPLETED" | "EXPIRED" | "CANCELLED" | "RECONCILIATION_REQUIRED";
export type ReservationStatus = "HELD" | "PAYMENT_LOCKED" | "CONFIRMED" | "CONSUMED" | "EXPIRED" | "CANCELLED";
export type ReceptionMode = "AT_COUNTER" | "PREBOOKED";

export interface RegistrationCreate {
  patientId: string;
  sessionId: string;
  expectedSessionRevision: number;
  receptionMode: ReceptionMode;
  visitType: "NEW_VISIT" | "FOLLOW_UP";
  previousRegistrationId: string | null;
  followUpRecommendationId: string | null;
  replacementReason: string | null;
  patientAgreedToDoctorReplacement: boolean | null;
  reason: string | null;
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

export interface FollowUpSourceSummary {
  registrationId: string;
  recordId: string;
  patientId: string;
  departmentId: string;
  doctorId: string;
  completedAt: string;
  recommendation: FollowUpRecommendationDTO | null;
}

export interface RevisionCommand {
  expectedRevision: number;
}

export interface RegistrationCancellationCommand {
  expectedRevision: number;
  reason: string;
}

export interface RegistrationDTO {
  registrationId: string;
  revision: number;
  patientId: string;
  recordId: string | null;
  followUpRecommendation: FollowUpRecommendationDTO | null;
  doctorId: string;
  departmentId: string;
  roomId: string;
  sessionId: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  receptionMode: ReceptionMode;
  visitType: "NEW_VISIT" | "FOLLOW_UP";
  previousRegistrationId: string | null;
  status: RegistrationStatus;
  reservationId: string;
  reservationRevision: number;
  reservationStatus: ReservationStatus;
  reservationExpiresAt: string;
  checkedInAt: string | null;
  queueNumber: number | null;
  readyForExam: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RegistrationSummary {
  registrationId: string;
  patientId: string;
  visitType: "NEW_VISIT" | "FOLLOW_UP";
  status: RegistrationStatus;
  checkedInAt: string | null;
  queueNumber: number | null;
  readyForExam: boolean;
}
