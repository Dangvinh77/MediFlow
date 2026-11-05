import type { CreateOutpatientPrescriptionRequest, PrescriptionLineRequest } from "./types";
import { isUuid } from "./utils";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";

type SubmittedRequest = Readonly<Omit<CreateOutpatientPrescriptionRequest, "lines">> & {
  readonly lines: readonly Readonly<PrescriptionLineRequest>[];
};

export interface OutpatientPrescriptionIntent {
  readonly commandId: string;
  readonly request: SubmittedRequest;
}

export interface RetainedOutpatientPrescriptionIntent {
  intent: OutpatientPrescriptionIntent;
  state: "in-flight" | "uncertain";
}

// Only the current authenticated session can read this memory-only recovery buffer.
// The submitted request stays in memory for an explicit same-key retry and is never
// written to browser storage or replayed automatically.
const retained = new Map<string, RetainedOutpatientPrescriptionIntent>();
const listeners = new Set<() => void>();
let observedSession: string | null | undefined;

function setObservedSession(session: string | null) {
  if (observedSession === session) return;
  observedSession = session;
  retained.clear();
  listeners.forEach(listener => listener());
}

if (typeof window !== "undefined") {
  observedSession = getSessionKey();
  subscribeToAuthChanges(() => setObservedSession(getSessionKey()));
}

function syncSession(session: string | null) {
  if (observedSession !== session) setObservedSession(session);
}

export function subscribeToRetainedOutpatientIntents(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getRetainedOutpatientPrescriptionIntent(session: string | null, recordId: string): RetainedOutpatientPrescriptionIntent | null {
  syncSession(session);
  return session ? retained.get(recordId) ?? null : null;
}

export function hasRetainedOutpatientPrescriptionIntent(session: string | null, recordId: string): boolean {
  return getRetainedOutpatientPrescriptionIntent(session, recordId) !== null;
}

export function retainOutpatientPrescriptionIntent(session: string | null, recordId: string, intent: OutpatientPrescriptionIntent, state: RetainedOutpatientPrescriptionIntent["state"]) {
  syncSession(session);
  if (!session) return;
  retained.set(recordId, { intent, state });
  listeners.forEach(listener => listener());
}

export function clearRetainedOutpatientPrescriptionIntent(session: string | null, recordId: string, expectedCommandId?: string) {
  syncSession(session);
  if (!session) return;
  const current = retained.get(recordId);
  if (!current || (expectedCommandId && current.intent.commandId !== expectedCommandId)) return;
  retained.delete(recordId);
  listeners.forEach(listener => listener());
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
      ...(line.note !== undefined ? { note: line.note } : {}),
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
