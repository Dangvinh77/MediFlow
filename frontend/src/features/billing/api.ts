import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { InvoiceDTO } from "./types";

export const billingApi = {
  byPatient: (patientId: string, page = 0, size = 20) =>
    api.get<PageResult<InvoiceDTO>>(
      `/v1/billing/patient/${encodeURIComponent(patientId)}?page=${page}&size=${size}`,
    ),
};
