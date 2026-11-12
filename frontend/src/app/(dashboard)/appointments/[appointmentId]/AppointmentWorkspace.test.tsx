import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { AppointmentWorkspace } from "./AppointmentWorkspace";

const status = vi.hoisted(() => ({ current: "IN_EXAM" }));
vi.mock("@/features/appointment/components/AppointmentDetail", () => ({
  AppointmentDetail: ({ renderRecordLink }: { renderRecordLink: (appointment: unknown) => ReactNode }) =>
    <div>{renderRecordLink({ appointmentId: "appointment-a", patientId: "patient-a", status: status.current })}</div>,
}));
vi.mock("@/features/medical-record/components/ExaminationRecordLink", () => ({
  ExaminationRecordLink: ({ label }: { label?: string }) => <span>{label ?? "default label"}</span>,
}));

describe("Appointment workspace record link", () => {
  it("offers continuing an examination in progress", () => {
    status.current = "IN_EXAM";
    render(<AppointmentWorkspace appointmentId="appointment-a" outpatientEnabled clinicIntakeEnabled={false} />);
    expect(screen.getByText("Tiếp tục khám")).toBeVisible();
  });

  it("keeps the default label after the examination ended", () => {
    status.current = "COMPLETED";
    render(<AppointmentWorkspace appointmentId="appointment-a" outpatientEnabled clinicIntakeEnabled={false} />);
    expect(screen.getByText("default label")).toBeVisible();
  });
});
