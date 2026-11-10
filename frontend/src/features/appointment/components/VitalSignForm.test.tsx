import { webcrypto } from "node:crypto";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { VitalSignForm } from "./VitalSignForm";
import { VitalSignHistory } from "./VitalSignHistory";

const mocks = vi.hoisted(() => ({ createVitalSigns: vi.fn(), listVitalSigns: vi.fn() }));
vi.mock("../api", () => ({ appointmentApi: mocks }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "nurse-session" }));

const marker = /bất thường|nguy hiểm|cảnh báo|vượt ngưỡng|\bcao\b|\bthấp\b/i;
const fill = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
function fillComplete() {
  fill("Mạch (lần/phút)", "245"); fill("Huyết áp tâm thu (mmHg)", "290"); fill("Huyết áp tâm trương (mmHg)", "150");
  fill("Nhiệt độ (°C)", "44.5"); fill("Nhịp thở (lần/phút)", "70"); fill("SpO2 (%)", "55"); fill("Cân nặng (kg)", "60.25");
}

describe("vital signs", () => {
  beforeEach(() => {
    Object.values(mocks).forEach(mock => mock.mockReset()); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear();
    mocks.createVitalSigns.mockResolvedValue({ observationId: "o1" });
  });

  it("vital form requires a reason when a measure is missing", async () => {
    const onSaved = vi.fn();
    render(<VitalSignForm registrationId="r1" onSaved={onSaved} />);
    const user = userEvent.setup();
    fill("Mạch (lần/phút)", "80");
    await user.click(screen.getByRole("button", { name: "Lưu sinh hiệu" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Cần ghi lý do");
    expect(mocks.createVitalSigns).not.toHaveBeenCalled();
    fill("Lý do chưa đo đủ", "Bệnh nhân không đứng được");
    await user.click(screen.getByRole("button", { name: "Lưu sinh hiệu" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(mocks.createVitalSigns.mock.calls[0][1]).toEqual({ pulseBpm: 80, systolicMmhg: null, diastolicMmhg: null, temperatureC: null, respiratoryRate: null, spo2Percent: null, weightKg: null, notMeasuredReason: "Bệnh nhân không đứng được" });
  });

  it("needs both blood pressure values", async () => {
    render(<VitalSignForm registrationId="r1" onSaved={vi.fn()} />);
    fillComplete(); fill("Huyết áp tâm trương (mmHg)", "");
    await userEvent.setup().click(screen.getByRole("button", { name: "Lưu sinh hiệu" }));
    expect(screen.getByRole("alert")).toHaveTextContent("cả huyết áp tâm thu và tâm trương");
    expect(mocks.createVitalSigns).not.toHaveBeenCalled();
  });

  it("does not show any abnormal marker", async () => {
    mocks.listVitalSigns.mockResolvedValue([{ observationId: "o1", registrationId: "r1", observedAt: "2026-11-10T01:00:00Z", recordedByAccountId: "a", recordedByStaffId: "s", pulseBpm: 245, systolicMmhg: 290, diastolicMmhg: 150, temperatureC: 44.5, respiratoryRate: 70, spo2Percent: 55, weightKg: 60.25, notMeasuredReason: null }]);
    const { container } = render(<><VitalSignForm registrationId="r1" onSaved={vi.fn()} /><VitalSignHistory registrationId="r1" /></>);
    fillComplete();
    expect(await screen.findByText(/245 lần\/phút/)).toBeVisible();
    await userEvent.setup().click(screen.getByRole("button", { name: "Lưu sinh hiệu" }));
    await waitFor(() => expect(mocks.createVitalSigns).toHaveBeenCalled());
    expect(container.textContent).not.toMatch(marker);
    expect(container.querySelector(".text-danger, .text-warning, .bg-danger\\/10")).toBeNull();
  });

  it("rejects a comma decimal with a Vietnamese message", async () => {
    render(<VitalSignForm registrationId="r1" onSaved={vi.fn()} />);
    fillComplete(); fill("Nhiệt độ (°C)", "36,5");
    await userEvent.setup().click(screen.getByRole("button", { name: "Lưu sinh hiệu" }));
    expect(screen.getByRole("alert")).toHaveTextContent("dùng dấu chấm");
    expect(mocks.createVitalSigns).not.toHaveBeenCalled();
  });

  it("does not read a failed history load as no measurement", async () => {
    mocks.listVitalSigns.mockRejectedValue(new Error("offline"));
    render(<VitalSignHistory registrationId="r1" />);
    expect(await screen.findByRole("button", { name: /Thử lại/ })).toBeVisible();
    expect(screen.queryByText(/Chưa có sinh hiệu/)).not.toBeInTheDocument();
  });
});
