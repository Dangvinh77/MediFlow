import { afterEach, beforeEach, expect, it, vi } from "vitest";
vi.mock("client-only", () => ({}));
import { api } from "./api";
import { clearSession, getSessionKey, getToken, renewAccessToken, storeSession } from "./session";

const fetchMock = vi.fn();
function token(extra: Record<string, unknown> = {}) {
  return `header.${btoa(JSON.stringify({ sub: "account-a", role: "DOCTOR", type: "access", ...extra }))}.signature`;
}
const oldToken = token({ exp: 1 }), freshToken = token({ exp: 9999999999 });
function login(accessToken = oldToken) { storeSession({ accessToken, refreshToken: "refresh-a", role: "DOCTOR" }); }
function success() { return Response.json({ success: true, data: { saved: true } }); }
function expired() { return Response.json({ message: "expired" }, { status: 401 }); }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => { localStorage.clear(); fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); login(); });
afterEach(() => { clearSession(); vi.unstubAllGlobals(); });

it("renews and retries the exact command once with the same idempotency key and body", async () => {
  const session = getSessionKey();
  fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(Response.json({ accessToken: freshToken })).mockResolvedValueOnce(success());
  await expect(api.post("/v1/org/staff", { fullName: "A" }, { idempotencyKey: "intent-a" })).resolves.toEqual({ saved: true });
  expect(fetchMock).toHaveBeenCalledTimes(3);
  expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/auth/refresh");
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ refreshToken: "refresh-a" });
  const original = fetchMock.mock.calls[0][1], retried = fetchMock.mock.calls[2][1];
  expect(retried.body).toBe(original.body);
  expect(retried.method).toBe(original.method);
  expect(new Headers(retried.headers).get("Idempotency-Key")).toBe("intent-a");
  expect(new Headers(retried.headers).get("Authorization")).toBe(`Bearer ${freshToken}`);
  expect(getSessionKey()).toBe(session);
});

it("shares one refresh among simultaneous 401s", async () => {
  const refresh = deferred<Response>();
  fetchMock.mockImplementation((url, init) => url === "/api/v1/auth/refresh" ? refresh.promise
    : Promise.resolve(new Headers(init.headers).get("Authorization") === `Bearer ${oldToken}` ? expired() : success()));
  const requests = [api.get("/v1/one"), api.get("/v1/two")];
  await vi.waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.endsWith("/refresh"))).toHaveLength(1));
  refresh.resolve(Response.json({ accessToken: freshToken }));
  await Promise.all(requests);
  expect(fetchMock.mock.calls.filter(([url]) => url.endsWith("/refresh"))).toHaveLength(1);
});

it("uses the renewed bearer for a late old-token 401 without another refresh", async () => {
  const late = deferred<Response>();
  fetchMock.mockReturnValueOnce(late.promise).mockResolvedValueOnce(expired())
    .mockResolvedValueOnce(Response.json({ accessToken: freshToken })).mockResolvedValueOnce(success()).mockResolvedValueOnce(success());
  const old = api.get("/v1/late");
  await api.get("/v1/first");
  late.resolve(expired());
  await old;
  expect(fetchMock.mock.calls.filter(([url]) => url.endsWith("/refresh"))).toHaveLength(1);
});

it("does not let an old refresh overwrite or replay a command into a new login", async () => {
  const refresh = deferred<Response>();
  fetchMock.mockResolvedValueOnce(expired()).mockReturnValueOnce(refresh.promise);
  const pending = expect(api.post("/v1/org/staff", { fullName: "A" })).rejects.toMatchObject({ status: 401 });
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  const newer = token({ sub: "account-b" }); login(newer);
  const newSession = getSessionKey();
  refresh.resolve(Response.json({ accessToken: freshToken }));
  await pending;
  expect(getToken()).toBe(newer); expect(getSessionKey()).toBe(newSession);
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

it("preserves a new login even when it receives exactly the same bearer as the old login", async () => {
  const late = deferred<Response>(); fetchMock.mockReturnValueOnce(late.promise);
  const pending = expect(api.get("/v1/late")).rejects.toMatchObject({ status: 401 });
  const originalSession = getSessionKey(); login();
  expect(getSessionKey()).not.toBe(originalSession);
  late.resolve(expired()); await pending;
  expect(getToken()).toBe(oldToken); expect(fetchMock).toHaveBeenCalledTimes(1);
});

it.each([401, 503])("handles refresh failure %i without replaying the command", async status => {
  fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(Response.json({ error: { code: "REFRESH_FAILED" }, correlationId: "trace-refresh" }, { status }));
  await expect(api.get("/v1/test")).rejects.toMatchObject({ status, code: "REFRESH_FAILED", correlationId: "trace-refresh" });
  expect(getToken()).toBe(status === 401 ? null : oldToken);
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

it("does not loop when the retried request is still unauthorized", async () => {
  fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(Response.json({ accessToken: freshToken })).mockResolvedValueOnce(expired());
  await expect(api.get("/v1/test")).rejects.toMatchObject({ status: 401 });
  expect(getToken()).toBeNull(); expect(fetchMock).toHaveBeenCalledTimes(3);
});

it.each([{ role: "ADMIN" }, { sub: "other-account" }, { departmentId: "new-department" }, { type: "refresh" }])("requires login when refreshed identity changes: %j", async claims => {
  fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(Response.json({ accessToken: token(claims) }));
  await expect(api.get("/v1/test")).rejects.toMatchObject({ status: 401 });
  expect(getToken()).toBeNull(); expect(fetchMock).toHaveBeenCalledTimes(2);
});

it("keeps the session on refresh network failure and permits a later attempt", async () => {
  fetchMock.mockResolvedValueOnce(expired()).mockRejectedValueOnce(new TypeError("offline"));
  await expect(api.get("/v1/test")).rejects.toThrow("offline");
  expect(getToken()).toBe(oldToken);
  fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(Response.json({ accessToken: freshToken })).mockResolvedValueOnce(success());
  await expect(api.get("/v1/test")).resolves.toEqual({ saved: true });
});

it.each(["network", "server"])("never replays an uncertain original write after %s failure", async kind => {
  if (kind === "network") fetchMock.mockRejectedValueOnce(new TypeError("offline"));
  else fetchMock.mockResolvedValueOnce(Response.json({ message: "failed" }, { status: 503 }));
  await expect(api.post("/v1/org/staff", { fullName: "A" })).rejects.toThrow();
  expect(fetchMock).toHaveBeenCalledTimes(1); expect(getToken()).toBe(oldToken);
});

it("does not auto-refresh login or refresh endpoints", async () => {
  fetchMock.mockResolvedValueOnce(expired());
  await expect(api.postRaw("/v1/auth/login", {})).rejects.toMatchObject({ status: 401 });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it("keeps legacy sessions stable during renewal and rejects renewal after logout", () => {
  localStorage.removeItem("mediflow.sessionId");
  const legacy = getSessionKey()!;
  expect(renewAccessToken(legacy, freshToken)).toBe(true);
  expect(getSessionKey()).toBe(legacy);
  clearSession();
  expect(renewAccessToken(legacy, oldToken)).toBe(false);
  expect(getToken()).toBeNull();
});
