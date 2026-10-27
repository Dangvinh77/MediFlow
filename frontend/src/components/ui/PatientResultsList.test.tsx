import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { PatientResultsList } from "./PatientResultsList";
import { clearSession, storeSession } from "@/lib/session";
import type { PageResult } from "@/lib/types";
const page = (name: string, number = 0): PageResult<string> => ({ content: [name], totalElements: 2, totalPages: 2, number, size: 1 });
beforeEach(() => { localStorage.clear(); });
it("pages own results and retries errors without accepting patient identifiers", async () => {
  const load = vi.fn().mockRejectedValueOnce(new Error("Mất kết nối")).mockResolvedValueOnce(page("Kết quả A")).mockResolvedValueOnce(page("Kết quả B", 1));
  render(<PatientResultsList<string> load={load} render={name => <p key={name}>{name}</p>} empty="Trống" />);
  await screen.findByRole("alert");
  fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  await screen.findByText("Kết quả A");
  fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
  await screen.findByText("Kết quả B");
  expect(load.mock.calls).toEqual([[0], [0], [1]]);
});
it("clears old results on session change and ignores the previous patient's late response", async () => {
  act(() => storeSession({ accessToken: "a", refreshToken: "ra", role: "PATIENT" }));
  let finish!: (value: PageResult<string>) => void;
  const load = vi.fn().mockImplementationOnce(() => new Promise(resolve => { finish = resolve; })).mockResolvedValue(page("B"));
  render(<PatientResultsList<string> load={load} render={name => <p key={name}>{name}</p>} empty="Trống" />);
  act(() => { clearSession(); storeSession({ accessToken: "b", refreshToken: "rb", role: "PATIENT" }); });
  await screen.findByText("B");
  await act(async () => finish(page("A")));
  expect(screen.queryByText("A")).not.toBeInTheDocument();
});
