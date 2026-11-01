import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { api } from "./api";
import { storeSession, clearSession } from "./session";
import { useCapabilities, refreshCapabilities } from "./capabilities";
vi.mock("./api", () => ({ api: { get: vi.fn() } }));
beforeEach(() => { clearSession(); vi.clearAllMocks(); });
it("loads live doctor authority and clears it when the login changes", async () => {
  storeSession({ role: "DOCTOR", accessToken: "a", refreshToken: "r" });
  vi.mocked(api.get).mockResolvedValue({ accountId: "a", staffId: "s", role: "DOCTOR", canPracticeAsDoctor: true, managementDepartments: [{ departmentId: "d", departmentName: "Nội", appointmentId: "h", revision: 2 }], observedAt: "now" });
  const { result } = renderHook(() => useCapabilities());
  await act(async () => { await refreshCapabilities(); });
  await waitFor(() => expect(result.current.data?.managementDepartments).toHaveLength(1));
  act(() => clearSession());
  await waitFor(() => expect(result.current.data).toBeNull());
});
it("discards an authority response from an earlier session", async () => {
  storeSession({ role: "DOCTOR", accessToken: "a", refreshToken: "r" });
  let resolve!: (value: unknown) => void;
  vi.mocked(api.get).mockReturnValue(new Promise(done => { resolve = done; }));
  const { result } = renderHook(() => useCapabilities());
  const pending = refreshCapabilities();
  act(() => clearSession());
  await act(async () => { resolve({ managementDepartments: [{ departmentId: "secret" }] }); await pending; });
  expect(result.current.data).toBeNull();
});

it("clears a previously granted department when live authority is revoked", async () => {
 storeSession({role:"DOCTOR",accessToken:"a",refreshToken:"r"});
 const {result}=renderHook(()=>useCapabilities());
 vi.mocked(api.get).mockResolvedValueOnce({role:"DOCTOR",managementDepartments:[{departmentId:"d",appointmentId:"h",revision:1}]}).mockResolvedValueOnce({role:"DOCTOR",managementDepartments:[]});
 await act(async()=>{await refreshCapabilities();});
 expect(result.current.data?.managementDepartments).toHaveLength(1);
 await act(async()=>{await refreshCapabilities();});
 expect(result.current.data?.managementDepartments).toHaveLength(0);
});
it("rejects capabilities belonging to a different role", async () => {
 storeSession({role:"DOCTOR",accessToken:"a",refreshToken:"r"});
 vi.mocked(api.get).mockResolvedValue({role:"MANAGER",managementDepartments:[{departmentId:"d"}]});
 const {result}=renderHook(()=>useCapabilities());
 await act(async()=>{await refreshCapabilities();});
 expect(result.current.data).toBeNull(); expect(result.current.error).toMatch(/không khớp/);
});
