import "client-only";

import { clearSession, getToken, getSessionKey, getRefreshToken, getRole, renewAccessToken } from "./session";
import type {
  ApiError,
  ApiResponse,
} from "./types";

// The single browser HTTP boundary. Feature paths start at `/v1`; this wrapper
// prepends same-origin `/api` so Next can proxy every request through the gateway.

export class ApiRequestError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
    public readonly details: ApiError["details"] = [],
    public readonly correlationId: string | null = null,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

interface LegacyErrorBody {
  error?:
    | string
    | {
        code?: string;
        message?: string;
        details?: ApiError["details"];
      };
  message?: string;
  correlationId?: string | null;
}

function isApiResponse(
  value: unknown,
): value is ApiResponse<unknown> {
  return (
    typeof value === "object" &&
    value !== null &&
    "success" in value
  );
}

let refreshFlight: { session: string; promise: Promise<void> } | null = null;

// Covers both response headers and body. A timeout never proves a write rolled back.
async function fetchJson(path: string, init: RequestInit): Promise<{ response: Response; body: unknown }> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15_000);
  try {
    const response = await fetch(path, { ...init, signal: controller.signal });
    const body: unknown = response.status === 204 ? null : await response.json().catch(cause => {
      if (controller.signal.aborted) throw cause;
      return null;
    });
    return { response, body };
  } catch (cause) {
    if (controller.signal.aborted) throw new ApiRequestError(
      "Yêu cầu quá thời gian chờ. Nếu đang lưu, hãy kiểm tra kết quả trước khi gửi lại.", 408, "CLIENT_REQUEST_TIMEOUT",
    );
    throw cause;
  } finally { clearTimeout(timer); }
}

function jwtIdentity(token: string): Record<string, unknown> | null {
  try {
    const part = token.split(".")[1];
    return JSON.parse(atob(part.replace(/-/g, "+").replace(/_/g, "/")));
  } catch { return null; }
}

async function refreshAccess(session: string, rejectedToken: string): Promise<void> {
  if (getSessionKey() !== session) throw new ApiRequestError("Phiên đăng nhập đã thay đổi.", 401);
  if (getToken() !== rejectedToken) return; // Another request/tab already renewed this login.
  if (refreshFlight?.session === session) return refreshFlight.promise;
  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new ApiRequestError("Phiên đăng nhập đã hết hạn.", 401);
  const promise = (async () => {
    const { response, body: payload } = await fetchJson("/api/v1/auth/refresh", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }), cache: "no-store",
    });
    const body = payload as { accessToken?: string; error?: ApiError; correlationId?: string } | null;
    if (getSessionKey() !== session) throw new ApiRequestError("Phiên đăng nhập đã thay đổi.", 401);
    if (!response.ok) {
      if (response.status === 401) clearSession();
      throw new ApiRequestError(
        response.status === 401 ? "Phiên đăng nhập đã hết hạn." : "Chưa thể gia hạn phiên. Hãy thử lại.",
        response.status, body?.error?.code, body?.error?.details ?? [], body?.correlationId ?? null,
      );
    }
    const accessToken = body?.accessToken;
    const identity = typeof accessToken === "string" ? jwtIdentity(accessToken) : null;
    const previous = jwtIdentity(rejectedToken);
    // These claims protect UI continuity only; signature verification belongs to Gateway.
    if (typeof accessToken !== "string" || !identity || !previous?.sub || identity.type !== "access" || identity.role !== getRole()
        || ["sub", "staffId", "departmentId", "patientId"].some(key => (identity[key] ?? null) !== (previous[key] ?? null))) {
      clearSession();
      throw new ApiRequestError("Thông tin truy cập đã thay đổi. Vui lòng đăng nhập lại.", 401);
    }
    renewAccessToken(session, accessToken);
  })();
  refreshFlight = { session, promise };
  try { await promise; } finally { if (refreshFlight?.promise === promise) refreshFlight = null; }
}

async function requestJson<T>(
  path: string,
  init?: RequestInit,
  retried = false,
  expectedSession?: string | null,
): Promise<T> {
  const session = getSessionKey();
  if (expectedSession !== undefined && session !== expectedSession) throw new ApiRequestError("Phiên đăng nhập đã thay đổi.", 401);
  const token = getToken();

  const headers = new Headers(init?.headers);

  if (init?.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const { response, body } = await fetchJson(`/api${path}`, {
    ...init,
    headers,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  if (!response.ok) {
    if (response.status === 401 && !retried && token && session && getSessionKey() === session
        && !path.startsWith("/v1/auth/") && getRefreshToken()) {
      await refreshAccess(session, token);
      return requestJson<T>(path, init, true, session);
    }
    // Remove the rejected bearer only while it still belongs to this session.
    // A late response (including a delayed body) must preserve a newer login.
    if (response.status === 401 && token && getToken() === token && getSessionKey() === session) {
      clearSession();
    }

    if (isApiResponse(body) && body.error) {
      throw new ApiRequestError(
        body.error.message,
        response.status,
        body.error.code,
        body.error.details ?? [],
        body.correlationId,
      );
    }

    const legacy = body as LegacyErrorBody | null;

    const nestedError =
      typeof legacy?.error === "object"
        ? legacy.error
        : undefined;

    const code =
      typeof legacy?.error === "string"
        ? legacy.error
        : nestedError?.code;

    throw new ApiRequestError(
      nestedError?.message ??
        legacy?.message ??
        `Request failed (${response.status})`,
      response.status,
      code,
      nestedError?.details ?? [],
      legacy?.correlationId ?? null,
    );
  }

  if (body === null) {
    throw new ApiRequestError(
      "Gateway returned an empty response",
      response.status,
    );
  }

  return body as T;
}

async function request<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const body = await requestJson<ApiResponse<T>>(
    path,
    init,
  );

  if (!body.success) {
    throw new ApiRequestError(
      body.error?.message ?? "Request failed",
      200,
      body.error?.code,
      body.error?.details ?? [],
      body.correlationId,
    );
  }

  if (body.data === null) {
    throw new ApiRequestError(
      "Gateway returned an empty success payload",
      200,
      undefined,
      [],
      body.correlationId,
    );
  }

  return body.data;
}

export interface MutationOptions {
  /** Stable key for one user intent; callers retain it when retrying that intent. */
  idempotencyKey?: string;
}

function mutationInit(
  method: "POST" | "PUT",
  data?: unknown,
  options?: MutationOptions,
): RequestInit {
  const init: RequestInit = { method };
  if (options?.idempotencyKey !== undefined) {
    const key = options.idempotencyKey;
    if (!key.trim() || key.length > 160 || /[\r\n]/.test(key)) {
      throw new Error("Idempotency-Key must contain 1–160 characters without line breaks.");
    }
    init.headers = { "Idempotency-Key": key };
  }
  // Some state-transition endpoints use POST/PUT without a JSON request body.
  if (data === undefined) {
    return init;
  }

  return {
    ...init,
    body: JSON.stringify(data),
  };
}

export const api = {
  get: <T>(path: string) =>
    request<T>(path, {
      method: "GET",
    }),

  post: <T>(
    path: string,
    data?: unknown,
    options?: MutationOptions,
  ) => request<T>(
    path,
    mutationInit("POST", data, options),
  ),

  postRaw: <T>(
    path: string,
    data?: unknown,
    options?: MutationOptions,
  ) => requestJson<T>(
    path,
    mutationInit("POST", data, options),
  ),

  put: <T>(
    path: string,
    data?: unknown,
    options?: MutationOptions,
  ) => request<T>(
    path,
    mutationInit("PUT", data, options),
  ),

  del: <T>(path: string) =>
    request<T>(path, {
      method: "DELETE",
    }),

  delEmpty: (path: string) =>
    requestJson<void>(path, {
      method: "DELETE",
    }),
};
