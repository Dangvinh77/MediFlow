import "client-only";

import { clearSession, getToken } from "./session";
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

async function requestJson<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const token = getToken();

  const headers = new Headers(init?.headers);

  if (init?.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`/api${path}`, {
    ...init,
    headers,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const body: unknown = await response
    .json()
    .catch(() => null);

  if (!response.ok) {
    // A rejected bearer token must not remain in localStorage. Otherwise the
    // dashboard can immediately render again with the same invalid session
    // after redirecting to the login page.
    if (response.status === 401) {
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

  return body.data as T;
}

function mutationInit(
  method: "POST" | "PUT",
  data?: unknown,
): RequestInit {
  // Some state-transition endpoints use POST/PUT without a JSON request body.
  if (data === undefined) {
    return { method };
  }

  return {
    method,
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
  ) => request<T>(
    path,
    mutationInit("POST", data),
  ),

  postRaw: <T>(
    path: string,
    data?: unknown,
  ) => requestJson<T>(
    path,
    mutationInit("POST", data),
  ),

  put: <T>(
    path: string,
    data?: unknown,
  ) => request<T>(
    path,
    mutationInit("PUT", data),
  ),

  del: <T>(path: string) =>
    request<T>(path, {
      method: "DELETE",
    }),
};
