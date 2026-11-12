"use client";

import { useCallback } from "react";
import { useCapabilities } from "@/lib/capabilities";
import type { MedicalRecordDTO, RecordCapabilitiesDTO, TreatmentCourseDTO } from "../types";
import { ConsultationNotesPanel } from "./ConsultationNotesPanel";
import { RecordAmendmentsPanel } from "./RecordAmendmentsPanel";
import { RecordHandoverPanel } from "./RecordHandoverPanel";
import { TreatmentCoursesPanel } from "./TreatmentCoursesPanel";

export function RecordCollaborationSection({ record, capabilities, onCommandSuccess }: {
  record: MedicalRecordDTO;
  capabilities: RecordCapabilitiesDTO | null;
  onCommandSuccess?: () => void;
}) {
  const identity = useCapabilities();
  const currentStaffId = identity.data?.staffId ?? null;
  const isDoctor = identity.data?.role === "DOCTOR" && identity.data.canPracticeAsDoctor;
  const managesDepartment = useCallback((departmentId: string) => identity.data?.role === "DOCTOR"
    && identity.data.managementDepartments.some((department) => department.departmentId === departmentId), [identity.data]);
  const canOpenCourse = isDoctor && currentStaffId !== null
    && (record.responsibleDoctorId ?? record.doctorId) === currentStaffId;
  const canAddCourseVisit = isDoctor && capabilities?.canRead === true;
  const canCloseCourse = (course: TreatmentCourseDTO) => currentStaffId !== null
    && (course.responsibleDoctorId === currentStaffId || managesDepartment(course.departmentId));

  return <div className="space-y-5">
    <TreatmentCoursesPanel key={`${identity.session}:${record.patientId}`} patientId={record.patientId} recordId={record.recordId} canOpen={canOpenCourse} canAddVisit={canAddCourseVisit} canCloseCourse={canCloseCourse} onCommandSuccess={onCommandSuccess} />
    <RecordHandoverPanel key={`${identity.session}:${record.recordId}:handover`} recordId={record.recordId} departmentId={record.departmentId} canHandover={capabilities?.canHandover === true} canAcceptHandover={capabilities?.canAcceptHandover === true} onCommandSuccess={onCommandSuccess} />
    <ConsultationNotesPanel key={`${identity.session}:${record.recordId}:notes`} recordId={record.recordId} canAddConsultationNote={capabilities?.canAddConsultationNote === true && record.status === "OPEN"} onCommandSuccess={onCommandSuccess} />
    <RecordAmendmentsPanel key={`${identity.session}:${record.recordId}:amendments`} record={record} recordId={record.recordId} departmentId={record.departmentId} canAmend={capabilities?.canAmend === true} canApplyAmendment={capabilities?.canApplyAmendment === true} canDecideAmendments={capabilities?.canDecideAmendments === true} canAssign={isDoctor && managesDepartment(record.departmentId)} currentStaffId={currentStaffId} onCommandSuccess={onCommandSuccess} />
  </div>;
}
