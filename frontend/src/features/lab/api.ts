import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  AddLabResultsRequest,
  AmendLabReportRequest,
  CancelLabTestRequest,
  LabReportDTO,
  CreateLabRequest,
  LabCareEpisodeType,
  LabCatalogGroup,
  LabCatalogItemDTO,
  LabTestTemplateDTO,
  LabTestDTO,
  LabTestStatus,
  StartLabTestRequest,
  SaveLabReportDraftRequest,
  SubmitLabReportRequest,
  ReturnLabReportRequest,
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
  catalog: (group?: LabCatalogGroup) =>
    api.get<LabCatalogItemDTO[]>(`/v2/lab/catalog${group ? `?group=${encodeURIComponent(group)}` : ""}`),
  getTemplate: (testId: string) =>
    api.get<LabTestTemplateDTO>(`/v2/lab/${encodeURIComponent(testId)}/template`),
  getReport: (testId: string) =>
    api.get<LabReportDTO>(`/v2/lab/${encodeURIComponent(testId)}/report`),
  saveReportDraft: (testId: string, body: SaveLabReportDraftRequest) =>
    api.put<LabReportDTO>(`/v2/lab/${encodeURIComponent(testId)}/report/draft`, body),
  submitReport: (testId: string, body: SubmitLabReportRequest) =>
    api.post<LabReportDTO>(`/v2/lab/${encodeURIComponent(testId)}/report/submission`, body),
  returnReport: (testId: string, body: ReturnLabReportRequest) =>
    api.post<LabReportDTO>(`/v2/lab/${encodeURIComponent(testId)}/report/return`, body),
  releaseReport: (testId: string) =>
    api.post<LabReportDTO>(`/v2/lab/${encodeURIComponent(testId)}/report/release`),
  amendReport: (testId: string, body: AmendLabReportRequest) =>
    api.post<LabReportDTO>(`/v2/lab/${encodeURIComponent(testId)}/report/amendments`, body),
  mine: (page = 0) => api.get<PageResult<import("./types").PatientLabResultDTO>>(`/v1/lab/mine?page=${page}&size=20`),
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
  start: (testId: string, body: StartLabTestRequest = { emergencyOverride: null }) =>
    api.put<LabTestDTO>(`/v1/lab/${encodeURIComponent(testId)}/start`, body),
  addResults: (testId: string, body: AddLabResultsRequest) =>
    api.put<LabTestDTO>(`/v1/lab/${encodeURIComponent(testId)}/results`, body),
  cancel: (testId: string, body: CancelLabTestRequest) =>
    api.put<LabTestDTO>(`/v1/lab/${encodeURIComponent(testId)}/cancel`, body),
};
