import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { DoctorOpenVisitsPanel } from "./DoctorOpenVisitsPanel";

const mocks = vi.hoisted(() => ({ myOpenVisits: vi.fn() }));
vi.mock("../api", () => ({ appointmentApi: { myOpenVisits: mocks.myOpenVisits } }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: ({ value }: { value: string }) => <span>{value}</span> }));

const visit = { registrationId: "registration-a", recordId: "record-a", doctorId: "doctor-a", departmentId: "department-a", sessionDate: "2026-11-09", startedAt: "2026-11-09T02:00:00Z" };

describe("Doctor open visits", () => {
  beforeEach(() => vi.clearAllMocks());

  it("doctor list opens the same record", async () => {
    mocks.myOpenVisits.mockResolvedValue([visit]);
    render(<DoctorOpenVisitsPanel enabled />);
    expect(await screen.findByRole("link", { name: "Tiếp tục khám" })).toHaveAttribute("href", "/records/record-a");
  });

  it("says when nothing is open", async () => {
    mocks.myOpenVisits.mockResolvedValue([]);
    render(<DoctorOpenVisitsPanel enabled />);
    expect(await screen.findByText("Không có lượt khám chưa kết thúc.")).toBeVisible();
  });

  it("outage shows an error, not an empty list", async () => {
    mocks.myOpenVisits.mockRejectedValue(new ApiRequestError("Lỗi kết nối", 503));
    render(<DoctorOpenVisitsPanel enabled />);
    expect(await screen.findByText("Lỗi kết nối")).toBeVisible();
    expect(screen.queryByText("Không có lượt khám chưa kết thúc.")).not.toBeInTheDocument();
  });

  it("does not read while scheduling is off", () => {
    const { container } = render(<DoctorOpenVisitsPanel enabled={false} />);
    expect(container).toBeEmptyDOMElement();
    expect(mocks.myOpenVisits).not.toHaveBeenCalled();
  });
});
