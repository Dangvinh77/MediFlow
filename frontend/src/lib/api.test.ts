import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("client-only", () => ({}));
const session = vi.hoisted(() => ({ getToken: vi.fn(), clearSession: vi.fn() }));
vi.mock("./session", () => session);
import { api, ApiRequestError } from "./api";

const fetchMock = vi.fn();
const key = "10000000-0000-4000-8000-000000000001";
function respond(status = 200, body: unknown = { success: true, data: { id: "server-id" } }) {
  fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(body), { status }));
}
function sent() { return fetchMock.mock.calls[0][1] as RequestInit; }

describe("shared POST idempotency transport", () => {
  beforeEach(() => {
    fetchMock.mockReset(); session.clearSession.mockReset();
    session.getToken.mockReturnValue("signed-token");
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("sends key, signed bearer and JSON through the same-origin Gateway", async () => {
    respond();
    await expect(api.post("/v1/pharmacy/prescriptions", { careContractVersion: 1 }, { idempotencyKey: key }))
      .resolves.toEqual({ id: "server-id" });
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/pharmacy/prescriptions", expect.anything());
    const headers = new Headers(sent().headers);
    expect(headers.get("Idempotency-Key")).toBe(key);
    expect(headers.get("Authorization")).toBe("Bearer signed-token");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(sent().method).toBe("POST");
    expect(sent().body).toBe(JSON.stringify({ careContractVersion: 1 }));
  });
  it("leaves legacy bodyless POST without a key or content type", async () => {
    session.getToken.mockReturnValue(null); respond();
    await api.post("/v1/pharmacy/admin/outbox/event/replay");
    expect(sent().body).toBeUndefined();
    expect(new Headers(sent().headers).get("Content-Type")).toBeNull();
    expect(new Headers(sent().headers).get("Idempotency-Key")).toBeNull();
    expect(new Headers(sent().headers).get("Authorization")).toBeNull();
  });
  it("does not leak the command key into subsequent requests", async () => {
    respond(); respond();
    await api.post("/v1/one", {}, { idempotencyKey: key });
    await api.post("/v1/two", {});
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get("Idempotency-Key")).toBeNull();
  });
  it.each([401, 403, 409, 503])("preserves error and correlation at %i without retry", async (status) => {
    respond(status, { success: false, data: null, error: {
      code: "COMMAND_REJECTED", message: "Rejected", details: [{ field: "priceCode", message: "Unknown" }],
    }, correlationId: "trace-id" });
    await expect(api.post("/v1/command", {}, { idempotencyKey: key })).rejects.toMatchObject({
      status, code: "COMMAND_REJECTED", correlationId: "trace-id",
      details: [{ field: "priceCode", message: "Unknown" }],
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(session.clearSession).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
  });
  it("does not retry a network failure", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("Connection lost"));
    await expect(api.post("/v1/command", {}, { idempotencyKey: key })).rejects.toThrow("Connection lost");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it("does not clear a newer session when an old bearer receives a late 401", async () => {
    let finish!: (response: Response) => void;
    fetchMock.mockReturnValueOnce(new Promise<Response>((resolve) => { finish = resolve; }));
    const request = api.get("/v1/records/old");
    const rejected = expect(request).rejects.toMatchObject({ status: 401 });
    session.getToken.mockReturnValue("new-session-token");
    finish(new Response(JSON.stringify({ message: "Expired old token" }), { status: 401 }));
    await rejected;
    expect(session.clearSession).not.toHaveBeenCalled();
    expect(new Headers(sent().headers).get("Authorization")).toBe("Bearer signed-token");
  });
  it("rejects empty success without generating a replacement command", async () => {
    respond(200, { success: true, data: null, correlationId: "trace-empty" });
    await expect(api.post("/v1/command", {}, { idempotencyKey: key }))
      .rejects.toEqual(expect.objectContaining({ name: ApiRequestError.name, correlationId: "trace-empty" }));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
