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
    // Remove the rejected bearer only while it still belongs to this session.
    // A late response (including a delayed body) must preserve a newer login.
    if (response.status === 401 && token && getToken() === token) {
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
