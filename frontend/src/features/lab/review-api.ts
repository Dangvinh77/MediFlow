import { api } from "@/lib/api";
import type {
  CollectLabSpecimenRequest,
  LabCriticalNoticeDTO,
  LabSpecimenDecisionRequest,
  LabSpecimenDTO,
  LabWorklistItemDTO,
} from "./types";

export const labReviewApi = {
  worklist: () => api.get<LabWorklistItemDTO[]>("/v2/lab/worklist"),
  specimens: (testId: string) =>
    api.get<LabSpecimenDTO[]>(`/v2/lab/${encodeURIComponent(testId)}/specimens`),
  collectSpecimen: (testId: string, body: CollectLabSpecimenRequest) =>
    api.post<LabSpecimenDTO>(`/v2/lab/${encodeURIComponent(testId)}/specimens`, body),
  decideSpecimen: (specimenId: string, body: LabSpecimenDecisionRequest) =>
    api.put<LabSpecimenDTO>(`/v2/lab/specimens/${encodeURIComponent(specimenId)}/decision`, body),
  criticalNotices: (status: "OPEN" | "ACKNOWLEDGED" = "OPEN") =>
    api.get<LabCriticalNoticeDTO[]>(`/v2/lab/critical-notices?status=${status}`),
  acknowledgeCriticalNotice: (testId: string, noticeId: string) =>
    api.post<LabCriticalNoticeDTO>(
      `/v2/lab/${encodeURIComponent(testId)}/critical-notices/${encodeURIComponent(noticeId)}/acknowledgement`,
    ),
};
