import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { OpenVisitNotice } from "./OpenVisitNotice";

const mocks = vi.hoisted(() => ({ openVisits: vi.fn() }));
vi.mock("../api", () => ({ appointmentApi: { openVisits: mocks.openVisits } }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: ({ value }: { value: string }) => <span>{value}</span> }));

const visit = { registrationId: "registration-a", recordId: "record-a", doctorId: "doctor-a", departmentId: "other-department", sessionDate: "2026-11-09", startedAt: "2026-11-09T02:00:00Z" };

describe("Open visit notice", () => {
  beforeEach(() => vi.clearAllMocks());

  it("warns about an open visit", async () => {
    mocks.openVisits.mockResolvedValue([visit]);
    const onSameDepartmentChange = vi.fn();
    render(<OpenVisitNotice patientId="patient-a" departmentId="department-a" reason="" onReasonChange={vi.fn()} onSameDepartmentChange={onSameDepartmentChange} />);
    expect(await screen.findByText(/còn 1 lượt khám chưa kết thúc/)).toBeVisible();
    expect(mocks.openVisits).toHaveBeenCalledWith("patient-a");
    expect(screen.queryByLabelText(/Lý do đăng ký thêm lượt/)).not.toBeInTheDocument();
    await waitFor(() => expect(onSameDepartmentChange).toHaveBeenLastCalledWith(false));
  });

  it("same department asks for a reason", async () => {
    mocks.openVisits.mockResolvedValue([{ ...visit, departmentId: "department-a" }]);
    const onReasonChange = vi.fn();
    const onSameDepartmentChange = vi.fn();
    render(<OpenVisitNotice patientId="patient-a" departmentId="department-a" reason="" onReasonChange={onReasonChange} onSameDepartmentChange={onSameDepartmentChange} />);
    fireEvent.change(await screen.findByLabelText(/Lý do đăng ký thêm lượt/), { target: { value: "Vấn đề mới" } });
    expect(onReasonChange).toHaveBeenCalledWith("Vấn đề mới");
    expect(onSameDepartmentChange).toHaveBeenLastCalledWith(true);
  });

  it("shows nothing when no visit is open", async () => {
    mocks.openVisits.mockResolvedValue([]);
    const { container } = render(<OpenVisitNotice patientId="patient-a" departmentId="department-a" reason="" onReasonChange={vi.fn()} onSameDepartmentChange={vi.fn()} />);
    await waitFor(() => expect(mocks.openVisits).toHaveBeenCalled());
    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });

  it("says the check is unavailable during an outage", async () => {
    mocks.openVisits.mockRejectedValue(new ApiRequestError("Lỗi", 503));
    render(<OpenVisitNotice patientId="patient-a" departmentId="department-a" reason="" onReasonChange={vi.fn()} onSameDepartmentChange={vi.fn()} />);
    expect(await screen.findByText(/Không kiểm tra được lượt khám đang mở/)).toBeVisible();
  });

  it("asks for a reason when the server reports a conflict the check missed", () => {
    mocks.openVisits.mockReturnValue(new Promise(() => undefined));
    render(<OpenVisitNotice patientId="patient-a" departmentId="department-a" reason="" onReasonChange={vi.fn()} onSameDepartmentChange={vi.fn()} forceReason />);
    expect(screen.getByLabelText(/Lý do đăng ký thêm lượt/)).toBeVisible();
  });
});
