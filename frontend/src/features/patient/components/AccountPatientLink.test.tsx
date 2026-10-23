import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { patientContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({ read: vi.fn(), session: vi.fn(() => "session-a") }));
vi.mock("../api", () => ({ patientApi: { getById: mocks.read } }));
vi.mock("@/lib/session", () => ({ getSessionKey: mocks.session }));
import { AccountPatientLink } from "./AccountPatientLink";

beforeEach(() => { vi.resetAllMocks(); mocks.session.mockReturnValue("session-a"); });

it("retries a failed patient name without hiding the account link", async () => {
  mocks.read.mockRejectedValueOnce(new Error("Offline")).mockResolvedValueOnce(patientContractFixture);
  render(<AccountPatientLink patientId={patientContractFixture.maBenhNhan} />);
  expect(await screen.findByRole("link", { name: /Chưa tải được tên/ })).toHaveAttribute("href", `/patients/${patientContractFixture.maBenhNhan}`);
  fireEvent.click(screen.getByRole("button", { name: "Tải lại tên bệnh nhân" }));
  expect(await screen.findByRole("link", { name: new RegExp(patientContractFixture.hoTen) })).toBeVisible();
});

it("ignores a late patient name after unmount", async () => {
  let resolve!: (patient: typeof patientContractFixture) => void;
  mocks.read.mockReturnValue(new Promise(done => { resolve = done; }));
  const view = render(<AccountPatientLink patientId={patientContractFixture.maBenhNhan} />);
  view.unmount();
  await act(async () => { resolve(patientContractFixture); });
  expect(screen.queryByRole("link")).not.toBeInTheDocument();
});
