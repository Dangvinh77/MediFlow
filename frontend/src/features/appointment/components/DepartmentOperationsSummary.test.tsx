import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { DepartmentOperationsSummary } from "./DepartmentOperationsSummary";
import { appointmentApi } from "../api";
vi.mock("../api", () => ({ appointmentApi: { sessionSummary: vi.fn() } }));
it("reports aggregate totals from the server rather than counting one page", async () => {
 vi.mocked(appointmentApi.sessionSummary).mockResolvedValue({departmentId:"d",fromDate:"2026-11-01",toDate:"2026-11-01",openSessionCount:31,stoppedSessionCount:2,cancelledSessionCount:1,capacity:620,heldCount:5,paymentLockedCount:3,confirmedCount:90,consumedCount:20,availableCount:502,observedAt:"2026-11-01T01:00:00Z"});
 render(<DepartmentOperationsSummary departmentId="d"/>);
 expect(await screen.findByText("620")).toBeInTheDocument();
 expect(screen.getByText("31")).toBeInTheDocument();
 expect(screen.getByText(/2026-11-01T01:00:00Z/)).toBeInTheDocument();
});
