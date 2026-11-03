import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { storeSession } from "@/lib/session";
const mocks=vi.hoisted(()=>({available:vi.fn(),router:{replace:vi.fn()}}));
vi.mock("../api",()=>({reportApi:{availablePeriods:mocks.available}}));
vi.mock("next/navigation",()=>({useRouter:()=>mocks.router}));
import { OutpatientAvailablePeriods } from "./OutpatientAvailablePeriods";
const item={coverageId:"proof",fromDate:"2025-01-01",toDate:"2025-12-31",departmentId:"00000000-0000-4000-8000-000000000001",currency:"VND",zoneId:"Asia/Bangkok",sourceObservedAt:"2026-01-01T00:00:00Z",reconciledAt:"2026-01-01T01:00:00Z",syntheticData:true};
beforeEach(()=>{vi.resetAllMocks();storeSession({accessToken:"test",refreshToken:"test",role:"ADMIN"});});
describe("available report periods",()=>{
 it("loads explicitly and selects a labelled synthetic scope without silently loading a report",async()=>{
   mocks.available.mockResolvedValue([item]);const select=vi.fn();render(<OutpatientAvailablePeriods mode="monthly" onSelect={select} />);
   expect(mocks.available).not.toHaveBeenCalled();fireEvent.click(screen.getByRole("button",{name:"Tìm kỳ có dữ liệu"}));
   await screen.findByText("Dữ liệu mô phỏng");expect(select).not.toHaveBeenCalled();
   fireEvent.click(screen.getByRole("button",{name:"Dùng kỳ này"}));expect(select).toHaveBeenCalledWith(item);
 });
 it("does not offer a partial year as a monthly full-year report",async()=>{
   mocks.available.mockResolvedValue([{...item,toDate:"2025-06-30"}]);render(<OutpatientAvailablePeriods mode="monthly" onSelect={vi.fn()} />);
   fireEvent.click(screen.getByRole("button",{name:"Tìm kỳ có dữ liệu"}));await screen.findByText(/Chưa tìm thấy kỳ phù hợp/);
   expect(screen.queryByRole("button",{name:"Dùng kỳ này"})).not.toBeInTheDocument();
 });
 it("ignores a discovery response after the scope was unmounted",async()=>{
   let resolve!:(items:typeof item[])=>void;mocks.available.mockReturnValue(new Promise(done=>{resolve=done}));
   const select=vi.fn();const view=render(<OutpatientAvailablePeriods mode="daily" onSelect={select} />);
   fireEvent.click(screen.getByRole("button",{name:"Tìm kỳ có dữ liệu"}));await waitFor(()=>expect(mocks.available).toHaveBeenCalled());
   view.unmount();resolve([item]);expect(select).not.toHaveBeenCalled();
 });
});
