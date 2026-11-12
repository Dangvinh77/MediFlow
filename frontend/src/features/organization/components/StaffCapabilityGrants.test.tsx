import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";

const mocks = vi.hoisted(() => ({ list: vi.fn(), grant: vi.fn(), revoke: vi.fn() }));
vi.mock("../capability-api", () => ({ capabilityApi: mocks }));

import { StaffCapabilityGrants } from "./StaffCapabilityGrants";

const staffId = "00000000-0000-4000-8000-000000000015";
const active = {
  grantId: "00000000-0000-4000-8000-0000000000a1", staffId, capability: "LAB_RESULT_RELEASE",
  grantedBy: "admin", grantReason: "Phân công phát hành", grantedAt: "2026-11-12T02:00:00Z",
  revokedBy: null, revocationReason: null, revokedAt: null,
};
const revoked = { ...active, grantId: "00000000-0000-4000-8000-0000000000a0", revokedBy: "admin",
  revocationReason: "Chuyển công tác", revokedAt: "2026-11-11T02:00:00Z" };

beforeEach(() => { vi.clearAllMocks(); mocks.list.mockResolvedValue([]); });

it("lists active and revoked grants", async () => {
  mocks.list.mockResolvedValue([active, revoked]);
  render(<StaffCapabilityGrants staffId={staffId} />);
  expect(await screen.findByText("Đang hiệu lực")).toBeInTheDocument();
  expect(screen.getByText("Đã thu hồi")).toBeInTheDocument();
  expect(screen.getByText(/Chuyển công tác/)).toBeInTheDocument();
  expect(mocks.list).toHaveBeenCalledWith(staffId);
});

it("grants lab release with a reason and reloads", async () => {
  mocks.grant.mockResolvedValue(active);
  render(<StaffCapabilityGrants staffId={staffId} />);
  await screen.findByText("Chưa có quyền đặc biệt nào.");
  mocks.list.mockResolvedValue([active]);
  fireEvent.change(screen.getByLabelText("Lý do cấp"), { target: { value: " Phân công phát hành " } });
  fireEvent.click(screen.getByRole("button", { name: "Cấp quyền" }));
  await waitFor(() => expect(mocks.grant).toHaveBeenCalledWith(staffId, "LAB_RESULT_RELEASE", "Phân công phát hành"));
  expect(await screen.findByText("Đang hiệu lực")).toBeInTheDocument();
  expect(screen.getByRole("status")).toHaveTextContent("Đã cấp quyền Phát hành kết quả xét nghiệm.");
});

it("blocks a grant without a reason", async () => {
  render(<StaffCapabilityGrants staffId={staffId} />);
  await screen.findByText("Chưa có quyền đặc biệt nào.");
  fireEvent.click(screen.getByRole("button", { name: "Cấp quyền" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Nhập lý do cấp quyền.");
  expect(mocks.grant).not.toHaveBeenCalled();
});

it("explains an ineligible grant inline", async () => {
  mocks.grant.mockRejectedValue(new ApiRequestError("raw", 422, "CAPABILITY_GRANT_INELIGIBLE"));
  render(<StaffCapabilityGrants staffId={staffId} />);
  await screen.findByText("Chưa có quyền đặc biệt nào.");
  fireEvent.change(screen.getByLabelText("Lý do cấp"), { target: { value: "x" } });
  fireEvent.click(screen.getByRole("button", { name: "Cấp quyền" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Kỹ thuật viên xét nghiệm");
});

it("revokes only after a reason is entered", async () => {
  mocks.list.mockResolvedValue([active]);
  mocks.revoke.mockResolvedValue({ ...active, revokedAt: "2026-11-12T03:00:00Z" });
  render(<StaffCapabilityGrants staffId={staffId} />);
  fireEvent.click(await screen.findByRole("button", { name: "Thu hồi" }));
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận thu hồi" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Nhập lý do thu hồi.");
  expect(mocks.revoke).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Lý do thu hồi"), { target: { value: "Hết phân công" } });
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận thu hồi" }));
  await waitFor(() => expect(mocks.revoke).toHaveBeenCalledWith(active.grantId, "Hết phân công"));
});
