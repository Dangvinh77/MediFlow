"use client";

import { AppointmentDetail } from "@/features/appointment/components/AppointmentDetail";
import { ExaminationRecordLink } from "@/features/medical-record/components/ExaminationRecordLink";

export function AppointmentWorkspace({ appointmentId, outpatientEnabled }: { appointmentId: string; outpatientEnabled: boolean }) {
  return <AppointmentDetail appointmentId={appointmentId} outpatientEnabled={outpatientEnabled}
    renderRecordLink={(appointment) => <ExaminationRecordLink key={appointment.appointmentId} patientId={appointment.patientId} appointmentId={appointment.appointmentId} />} />;
}
