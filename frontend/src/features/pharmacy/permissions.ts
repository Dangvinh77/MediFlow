import type { Role } from "@/lib/roles";
import type { PrescriptionDTO } from "./types";

/**
 * UX capabilities mirrored from Pharmacy controller roles.
 * These checks only control visibility; backend @PreAuthorize remains authoritative.
 */
export interface PharmacyCapabilities {
  canReadDrugs: boolean;
  canCreateDrug: boolean;
  canAdjustStock: boolean;
  canCreatePrescription: boolean;
  canReadPrescription: boolean;
  canCancelPrescription: boolean;
  canDispensePrescription: boolean;
  canReplayOutbox: boolean;
}

const noCapabilities: PharmacyCapabilities = {
  canReadDrugs: false,
  canCreateDrug: false,
  canAdjustStock: false,
  canCreatePrescription: false,
  canReadPrescription: false,
  canCancelPrescription: false,
  canDispensePrescription: false,
  canReplayOutbox: false,
};

export const pharmacyCapabilities = {
  ADMIN: {
    canReadDrugs: true,
    canCreateDrug: true,
    canAdjustStock: true,
    canCreatePrescription: true,
    canReadPrescription: true,
    canCancelPrescription: true,
    canDispensePrescription: true,
    canReplayOutbox: true,
  },

  DOCTOR: {
    canReadDrugs: true,
    canCreateDrug: false,
    canAdjustStock: false,
    canCreatePrescription: true,
    canReadPrescription: true,
    canCancelPrescription: true,
    canDispensePrescription: false,
    canReplayOutbox: false,
  },

  PHARMACIST: {
    canReadDrugs: true,
    canCreateDrug: true,
    canAdjustStock: true,
    canCreatePrescription: false,
    canReadPrescription: true,
    canCancelPrescription: false,
    canDispensePrescription: true,
    canReplayOutbox: false,
  },

  NURSE: noCapabilities,
  CASHIER: noCapabilities,
  LAB_TECH: noCapabilities,
  MANAGER: noCapabilities,
  PATIENT: noCapabilities,
  SYSTEM: noCapabilities,
} satisfies Record<Role, PharmacyCapabilities>;

export function getPharmacyCapabilities(
  role: Role | null,
): PharmacyCapabilities {
  return role ? pharmacyCapabilities[role] : noCapabilities;
}

function isMutablePrescription(
  prescription: PrescriptionDTO,
): boolean {
  // Both cancel and manual dispense are terminal transitions from this state pair.
  return (
    prescription.status === "ACTIVE" &&
    prescription.dispenseStatus === "PENDING"
  );
}

export function canCancelPrescription(
  role: Role | null,
  prescription: PrescriptionDTO,
): boolean {
  return (
    getPharmacyCapabilities(role).canCancelPrescription &&
    isMutablePrescription(prescription)
  );
}

export function canDispensePrescription(
  role: Role | null,
  prescription: PrescriptionDTO,
): boolean {
  return (
    getPharmacyCapabilities(role).canDispensePrescription &&
    isMutablePrescription(prescription)
  );
}
