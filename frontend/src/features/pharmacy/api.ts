import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { OutpatientPrescriptionIntent } from "./outpatientPrescriptionIntent";

import type {
  AdjustStockRequest,
  CancelPrescriptionRequest,
  CancelPrescriptionResult,
  CreateDrugRequest,
  CreatePrescriptionRequest,
  DispenseDTO,
  DrugDTO,
  OutboxReplayResult,
  PrescriptionDTO,
  PrescribingContextDTO,
  PharmacyPatientIdentityDTO,
} from "./types";

// lib/api.ts prepends `/api`; feature APIs only declare the gateway resource path.
const BASE = "/v1/pharmacy";

export interface DrugSearchParams {
  keyword?: string;
  filter?: import("./utils").DrugCatalogFilter;
  page?: number;
  size?: number;
}

export const pharmacyApi = {
  searchPatients(keyword: string, page = 0) {
    const query = new URLSearchParams({ keyword, page: String(page), size: "20" });
    return api.get<PageResult<PharmacyPatientIdentityDTO>>(`/v1/patients/directory/search?${query}`);
  },
  getPatientIdentity(id: string) {
    return api.get<PharmacyPatientIdentityDTO>(`/v1/patients/directory/${encodeURIComponent(id)}`);
  },
  searchPrescriptions(params: { keyword?: string; patientId?: string; status?: string; page?: number; size?: number } = {}) {
    const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 20) });
    for (const key of ["keyword", "patientId", "status"] as const) {
      if (params[key]?.trim()) query.set(key, params[key]!.trim());
    }
    return api.get<PageResult<PrescriptionDTO>>(`${BASE}/prescriptions?${query}`);
  },
  getPrescribingContext(recordId: string) {
    return api.get<PrescribingContextDTO>(`${BASE}/prescriptions/prescribing/context?${new URLSearchParams({ recordId })}`);
  },
  searchDrugs(params: DrugSearchParams = {}) {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });

    if (params.filter && params.filter !== "ALL") query.set("filter", params.filter);
    const keyword = params.keyword?.trim();

    if (keyword) {
      query.set("keyword", keyword);
    }

    return api.get<PageResult<DrugDTO>>(
      `${BASE}/drugs?${query.toString()}`,
    );
  },

  getDrug(drugId: string) {
    return api.get<DrugDTO>(`${BASE}/drugs/${drugId}`);
  },

  createDrug(body: CreateDrugRequest) {
    return api.post<DrugDTO>(`${BASE}/drugs`, body);
  },

  adjustStock(drugId: string, body: AdjustStockRequest) {
    return api.put<DrugDTO>(`${BASE}/drugs/${drugId}/stock`, body);
  },

  createPrescription(body: CreatePrescriptionRequest) {
    return api.post<PrescriptionDTO>(`${BASE}/prescriptions`, body);
  },

  createOutpatientPrescription(intent: OutpatientPrescriptionIntent) {
    return api.post<PrescriptionDTO>(`${BASE}/prescriptions`, intent.request, {
      idempotencyKey: intent.commandId,
    });
  },

  getPrescription(prescriptionId: string) {
    return api.get<PrescriptionDTO>(
      `${BASE}/prescriptions/${prescriptionId}`,
    );
  },

  cancelPrescription(
    prescriptionId: string,
    body: CancelPrescriptionRequest,
  ) {
    return api.put<CancelPrescriptionResult>(
      `${BASE}/prescriptions/${prescriptionId}/cancel`,
      body,
    );
  },

  dispensePrescription(prescriptionId: string) {
    // The live endpoint takes identity from the signed token and has no request body.
    return api.put<DispenseDTO>(
      `${BASE}/prescriptions/${prescriptionId}/dispense`,
    );
  },

  replayOutbox(eventId: string) {
    // Replay targets one known event id; the backend has no outbox list endpoint.
    return api.post<OutboxReplayResult>(
      `${BASE}/admin/outbox/${eventId}/replay`,
    );
  },
};
