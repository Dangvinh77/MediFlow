export interface DepartmentProfile {
  description: string | null;
  phoneNumber: string | null;
  email: string | null;
}
export interface StaffProfile {
  dateOfBirth: string | null;
  gender: "MALE" | "FEMALE" | "OTHER" | null;
  address: string | null;
  hireDate: string | null;
  qualification: string | null;
}

export interface StaffDepartmentDTO {
  departmentId: string;
  departmentName: string;
}

export type DepartmentType = "CLINICAL" | "PARACLINICAL" | "ADMINISTRATIVE";

export type JobTitle =
  | "DOCTOR"
  | "NURSE"
  | "TECHNICIAN"
  | "PHARMACIST"
  | "CASHIER"
  | "ADMINISTRATIVE";

export interface DepartmentDTO extends DepartmentProfile {
  departmentId: string;
  departmentName: string;
  abbreviation: string;
  departmentType: DepartmentType;
  departmentHeadId: string | null;
  location: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDepartmentRequest {
  profile?: DepartmentProfile;
  departmentName: string;
  abbreviation: string;
  departmentType: DepartmentType;
  location: string | null;
}

export interface UpdateDepartmentRequest {
  profile?: DepartmentProfile;
  departmentName: string;
  departmentType: DepartmentType;
  location: string | null;
  departmentHeadId: string | null;
  active: boolean | null;
}

export interface ChangeStaffDepartmentRequest {
  newDepartmentId: string;
}

export interface UpdateAccountStatusRequest {
  isActive: boolean;
  replaceActiveAccount?: boolean;
}

export interface CreateStaffRequest {
  profile?: StaffProfile;
  identityNumber: string;
  fullName: string;
  departmentId: string;
  jobTitle: JobTitle;
  specialization: string | null;
  licenseNumber: string | null;
  phoneNumber: string | null;
  email: string;
}

export type UpdateStaffRequest = Omit<CreateStaffRequest, "departmentId" | "identityNumber" | "email"> & {
  identityNumber?: string;
  identityChangeReason?: string;
  email: string | null;
};

export interface MailDelivery { messageId: string; status: "PENDING" | "SENT" | "FAILED" }

export type AccountRole =
  | "ADMIN" | "DOCTOR" | "NURSE" | "PHARMACIST" | "CASHIER"
  | "LAB_TECH" | "PATIENT" | "SYSTEM";

export interface AccountDTO {
  mailDelivery?: MailDelivery | null;
  accountId: string;
  username: string;
  staffId: string | null;
  patientId: string | null;
  role: AccountRole;
  isActive: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProfileAccountDTO {
  account: AccountDTO | null;
  profileEmail: string | null;
  emailSyncPending: boolean;
}

export interface CreateAccountRequest {
  replaceActiveAccount?: boolean;
  username: string;
  password?: string;
  staffId: string | null;
  patientId: string | null;
  role: AccountRole;
}

export interface StaffDTO extends StaffProfile {
  identityNumber: string | null;
  staffId: string;
  fullName: string;
  departmentId: string;
  jobTitle: JobTitle;
  specialization: string | null;
  licenseNumber: string | null;
  phoneNumber: string | null;
  email: string | null;
  active: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}
export interface IssuedPatientAccount {
  mailDelivery?: MailDelivery | null;
  accountId: string;
  patientId: string;
  username: string;
  mustChangePassword: boolean;
}
export interface HeadAppointmentSnapshot {
  departmentId: string; revision: number; appointmentId: string | null; staffId: string | null; accountId: string | null;
  updatedAt: string | null; updatedByAccountId: string | null;
  history: { appointmentId: string; staffId: string; accountId: string; startedAt: string; endedAt: string | null }[];
}
export interface HeadAppointmentCommand { expectedRevision: number; staffId: string | null; reason: string }

/** Code of a room type catalogue entry (Core's room_type_catalog), e.g. "CONSULTATION". */
export type RoomType = string;

/** Room type catalogue entry; mirrors Core's RoomTypeResponse. */
export interface RoomTypeDTO {
  code: RoomType;
  name: string;
  description: string | null;
  departmentType: DepartmentType;
  singlePerDepartment: boolean;
  schedulable: boolean;
  systemDefined: boolean;
  roomCount: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface CreateRoomTypeRequest {
  name: string;
  description: string | null;
  departmentType: DepartmentType;
  singlePerDepartment: boolean;
}

export interface UpdateRoomTypeRequest {
  name: string;
  description: string | null;
}

export interface RoomDTO {
  roomId: string;
  departmentId: string;
  roomType: RoomType;
  roomNumber: string;
  roomName: string;
  zone: string;
  location: string;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface RoomPlacement {
  roomNumber: string;
  roomName: string;
  zone: string;
  location: string;
}

export interface CreateRoomRequest extends RoomPlacement {
  departmentId: string;
  roomType: RoomType;
}

/** Capabilities an ADMIN grants explicitly; a role never implies them (Core StaffCapabilityGrantService). */
export type StaffCapability = "LAB_RESULT_RELEASE" | "MEDICAL_RECORD_REVIEW";

/** Mirrors Core StaffCapabilityGrantDTO. */
export interface StaffCapabilityGrantDTO {
  grantId: string;
  staffId: string;
  capability: StaffCapability;
  grantedBy: string;
  grantReason: string;
  grantedAt: string;
  revokedBy: string | null;
  revocationReason: string | null;
  revokedAt: string | null;
}
