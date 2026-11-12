"use client";
import { useLocale } from "@/components/i18n/LocaleProvider";


import { AppointmentDetail } from "@/features/appointment/components/AppointmentDetail";
import { ExaminationRecordLink } from "@/features/medical-record/components/ExaminationRecordLink";

export function AppointmentWorkspace({ appointmentId, outpatientEnabled, clinicIntakeEnabled }: { appointmentId: string; outpatientEnabled: boolean; clinicIntakeEnabled: boolean }) {
  const { localize } = useLocale();
  return <AppointmentDetail appointmentId={appointmentId} outpatientEnabled={outpatientEnabled} clinicIntakeEnabled={clinicIntakeEnabled}
    renderRecordLink={(appointment) => <ExaminationRecordLink key={appointment.appointmentId} patientId={appointment.patientId} appointmentId={appointment.appointmentId} label={localize(appointment.status === "IN_EXAM" ? "Tiếp tục khám" : undefined)} />} />;
}
