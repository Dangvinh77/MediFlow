import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";

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
} from "./types";

// lib/api.ts prepends `/api`; feature APIs only declare the gateway resource path.
const BASE = "/v1/pharmacy";

export interface DrugSearchParams {
  keyword?: string;
  page?: number;
  size?: number;
}

export const pharmacyApi = {
  searchDrugs(params: DrugSearchParams = {}) {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });

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
