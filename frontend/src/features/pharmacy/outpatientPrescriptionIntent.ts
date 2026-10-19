import type { CreateOutpatientPrescriptionRequest, PrescriptionLineRequest } from "./types";
import { isUuid } from "./utils";

type SubmittedRequest = Readonly<Omit<CreateOutpatientPrescriptionRequest, "lines">> & {
  readonly lines: readonly Readonly<PrescriptionLineRequest>[];
};

export interface OutpatientPrescriptionIntent {
  readonly commandId: string;
  readonly request: SubmittedRequest;
}

function snapshot(request: CreateOutpatientPrescriptionRequest): SubmittedRequest {
  // Copy only wire fields, in a stable order; draft/cached-drug mutations cannot alter a retry.
  return Object.freeze({
    recordId: request.recordId,
    patientId: request.patientId,
    doctorId: request.doctorId,
    departmentId: request.departmentId,
    prescribedDate: request.prescribedDate,
    lines: Object.freeze(request.lines.map((line) => Object.freeze({
      drugId: line.drugId,
      quantity: line.quantity,
      ...(line.dosage !== undefined ? { dosage: line.dosage } : {}),
    }))),
    careContractVersion: request.careContractVersion,
    careContext: request.careContext,
    careEpisodeType: request.careEpisodeType,
    careEpisodeId: request.careEpisodeId,
    admissionId: request.admissionId,
    priceCode: request.priceCode,
  });
}

/**
 * Retain the submitted command for an explicit retry of the same payload. A changed payload
 * is a new command. The form must clear the prior intent on success, new-command intent,
 * actor change or unmount, and must not automatically resend after an uncertain outcome.
 */
export function prepareOutpatientPrescriptionIntent(
  request: CreateOutpatientPrescriptionRequest,
  previous: OutpatientPrescriptionIntent | null = null,
  newCommandId: () => string = () => crypto.randomUUID(),
): OutpatientPrescriptionIntent {
  const submitted = snapshot(request);
  if (previous && JSON.stringify(previous.request) === JSON.stringify(submitted)) return previous;

  const commandId = newCommandId();
  if (commandId !== commandId.trim() || !isUuid(commandId)) {
    throw new Error("Mã yêu cầu kê đơn phải là UUID đúng định dạng.");
  }
  if (previous?.commandId.toLowerCase() === commandId.toLowerCase()) {
    throw new Error("Yêu cầu thay đổi phải có mã yêu cầu mới.");
  }
  return Object.freeze({ commandId, request: submitted });
}
