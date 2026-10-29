import { ApiRequestError } from "@/lib/api";
import type { DepartmentDTO, DepartmentProfile, StaffDTO, UpdateStaffRequest } from "./types";

export function isSaveOutcomeUnknown(cause: unknown) {
  return !(cause instanceof ApiRequestError)
    || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

export type DepartmentSaveDraft = Pick<DepartmentDTO,
  "departmentName" | "abbreviation" | "departmentType" | "location" | "departmentHeadId" | "active"
> & { profile: DepartmentProfile };

export function departmentMatchesDraft(current: DepartmentDTO, draft: DepartmentSaveDraft) {
  return current.departmentName === draft.departmentName
    && current.abbreviation === draft.abbreviation
    && current.departmentType === draft.departmentType
    && current.location === draft.location
    && current.departmentHeadId === draft.departmentHeadId
    && current.active === draft.active
    && current.description === draft.profile.description
    && current.phoneNumber === draft.profile.phoneNumber
    && current.email === draft.profile.email;
}

export interface StaffSaveDraft {
  body: UpdateStaffRequest;
  departmentId: string;
  transferRequested: boolean;
  stage: "profile" | "transfer";
}

export function staffMatchesDraft(current: StaffDTO, draft: StaffSaveDraft) {
  const body = draft.body;
  return current.fullName === body.fullName
    && current.jobTitle === body.jobTitle
    && current.specialization === body.specialization
    && current.licenseNumber === body.licenseNumber
    && current.phoneNumber === body.phoneNumber
    && current.email === body.email
    && current.dateOfBirth === body.profile?.dateOfBirth
    && current.gender === body.profile?.gender
    && current.address === body.profile?.address
    && current.hireDate === body.profile?.hireDate
    && current.qualification === body.profile?.qualification
    && (draft.stage === "profile" || current.departmentId === draft.departmentId);
}
