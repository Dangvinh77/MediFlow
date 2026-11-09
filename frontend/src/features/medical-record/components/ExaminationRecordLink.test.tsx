import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";

const mocks = vi.hoisted(() => ({ byPatient: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ medicalRecordApi: { byPatient: mocks.byPatient } }));
import { ExaminationRecordLink } from "./ExaminationRecordLink";

describe("Examination record link", () => {
  beforeEach(() => { mocks.byPatient.mockReset(); });

  it("shows no record link and no retry when reading is not permitted", async () => {
    mocks.byPatient.mockRejectedValue(new ApiRequestError("Forbidden", 403, "RECORD_ACCESS_DENIED"));
    render(<ExaminationRecordLink patientId="patient-a" appointmentId="appointment-a" />);
    expect(await screen.findByText("Bạn không có quyền xem hồ sơ khám của lịch hẹn này.")).toBeVisible();
    expect(screen.queryByRole("link", { name: "Mở hồ sơ khám" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("still offers a retry for other failures", async () => {
    mocks.byPatient.mockRejectedValue(new ApiRequestError("Lỗi máy chủ", 500, "INTERNAL_ERROR"));
    render(<ExaminationRecordLink patientId="patient-a" appointmentId="appointment-a" />);
    expect(await screen.findByRole("button")).toBeVisible();
  });
});
