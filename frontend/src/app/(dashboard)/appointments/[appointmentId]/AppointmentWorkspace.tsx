"use client";

import { AppointmentDetail } from "@/features/appointment/components/AppointmentDetail";
import { ExaminationRecordLink } from "@/features/medical-record/components/ExaminationRecordLink";

export function AppointmentWorkspace({ appointmentId, outpatientEnabled, clinicIntakeEnabled }: { appointmentId: string; outpatientEnabled: boolean; clinicIntakeEnabled: boolean }) {
  return <AppointmentDetail appointmentId={appointmentId} outpatientEnabled={outpatientEnabled} clinicIntakeEnabled={clinicIntakeEnabled}
    renderRecordLink={(appointment) => <ExaminationRecordLink key={appointment.appointmentId} patientId={appointment.patientId} appointmentId={appointment.appointmentId} />} />;
}
