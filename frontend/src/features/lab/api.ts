import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { LabTestDTO } from "./types";

export const labApi = {
  search: (page = 0, size = 20) =>
    api.get<PageResult<LabTestDTO>>(`/v1/lab?page=${page}&size=${size}`),
};
