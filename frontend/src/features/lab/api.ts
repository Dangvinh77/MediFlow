import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  CreateLabRequest,
  LabCareEpisodeType,
  LabTestDTO,
  LabTestStatus,
} from "./types";

export interface LabSearchParams {
  departmentId?: string;
  status?: LabTestStatus;
  episodeType?: LabCareEpisodeType;
  episodeId?: string;
  page?: number;
  size?: number;
}

export const labApi = {
  getById: (testId: string) =>
    api.get<LabTestDTO>(`/v1/lab/${encodeURIComponent(testId)}`),
  search: (params: LabSearchParams = {}) => {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });
    if (params.departmentId) query.set("departmentId", params.departmentId);
    if (params.status) query.set("status", params.status);
    if (params.episodeType) query.set("episodeType", params.episodeType);
    if (params.episodeId) query.set("episodeId", params.episodeId);
    return api.get<PageResult<LabTestDTO>>(`/v1/lab?${query}`);
  },
  create: (body: CreateLabRequest) =>
    api.post<LabTestDTO>("/v1/lab", body),
};
