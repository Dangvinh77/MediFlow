export type AppointmentStatus = "PENDING" | "ARRIVED" | "CANCELLED";

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
  updatedAt: string;
}
