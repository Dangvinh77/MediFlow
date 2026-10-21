import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
import { usePatientMutation } from "./usePatientMutation";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

describe("Patient mutation lifecycle without UI", () => {
  beforeEach(() => clearSession());
  it("locks duplicate requests synchronously and keeps success terminal", async () => {
    const response = deferred<boolean>();
    const operation = vi.fn(() => response.promise);
    const { result } = renderHook(() => usePatientMutation("patient"));
    let first!: Promise<boolean | undefined>;
    act(() => { first = result.current.run(operation); void result.current.run(operation); });
    expect(operation).toHaveBeenCalledTimes(1);
    await act(async () => { response.resolve(true); await first; });
    expect(result.current.blocked).toBe(true);
    await act(async () => { await result.current.run(operation); });
    expect(operation).toHaveBeenCalledTimes(1);
  });
  it.each([new Error("offline"), new ApiRequestError("timeout", 408), new ApiRequestError("unavailable", 503)])("does not replay an unknown result even after clearing feedback: %s", async (cause) => {
    const operation = vi.fn().mockRejectedValue(cause);
    const { result } = renderHook(() => usePatientMutation("patient"));
    await act(async () => { await result.current.run(operation); });
    expect(result.current.outcomeUnknown).toBe(true);
    act(() => result.current.clearError());
    expect(result.current.uncertainError).toBeTruthy();
    await act(async () => { await result.current.run(operation); });
    expect(result.current.blocked).toBe(true);
    expect(operation).toHaveBeenCalledTimes(1);
  });
  it("permits a corrected request after a definite validation rejection and preserves trace", async () => {
    const operation = vi.fn().mockRejectedValueOnce(new ApiRequestError("duplicate", 409, "PATIENT_CMND_DUPLICATE", [], "trace")).mockResolvedValueOnce(true);
    const { result } = renderHook(() => usePatientMutation("patient"));
    await act(async () => { await result.current.run(operation); });
    expect(result.current.error).toMatchObject({ correlationId: "trace", fieldErrors: { soCmnd: "duplicate" } });
    expect(result.current.blocked).toBe(false);
    await act(async () => { await result.current.run(operation); });
    expect(operation).toHaveBeenCalledTimes(2);
  });
  it("does not return a successful mutation from an unmounted workspace", async () => {
    const response = deferred<boolean>();
    const { result, unmount } = renderHook(() => usePatientMutation("patient-one"));
    const callback = vi.fn();
    let pending!: Promise<boolean | undefined>;
    act(() => { pending = result.current.run(() => response.promise, callback); });
    unmount();
    response.resolve(true);
    await expect(pending).resolves.toBeUndefined();
    expect(callback).not.toHaveBeenCalled();
  });
  it("does not return an old account's result after session replacement", async () => {
    storeSession({ accessToken: "old", refreshToken: "r", role: "ADMIN" });
    const response = deferred<boolean>();
    const { result } = renderHook(() => usePatientMutation("patient"));
    let pending!: Promise<boolean | undefined>;
    act(() => { pending = result.current.run(() => response.promise); });
    act(() => storeSession({ accessToken: "new", refreshToken: "r", role: "ADMIN" }));
    await act(async () => { response.resolve(true); await pending; });
    await expect(pending).resolves.toBeUndefined();
  });
});
