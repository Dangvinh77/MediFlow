// Thin fetch wrapper around the gateway. All calls go to /api/* which Next proxies
// to the gateway (see next.config.ts). Attaches the JWT and unwraps ApiResponse.

import { getToken } from "./session";
import type { ApiResponse } from "./types";

export class ApiRequestError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

interface ErrorBody {
  error?: string | { code?: string; message?: string };
  message?: string;
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const headers = new Headers(init?.headers);
  headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const res = await fetch(`/api${path}`, { ...init, headers });

  // 204 No Content
  if (res.status === 204) return undefined as T;

  const body = (await res.json().catch(() => null)) as T | ErrorBody | null;

  if (!res.ok) {
    const errorBody = body as ErrorBody | null;
    const nestedError =
      typeof errorBody?.error === "object" ? errorBody.error : undefined;
    const code =
      typeof errorBody?.error === "string" ? errorBody.error : nestedError?.code;
    const message =
      nestedError?.message ?? errorBody?.message ?? `Request failed (${res.status})`;
    throw new ApiRequestError(message, res.status, code);
  }

  if (body === null) {
    throw new ApiRequestError("Gateway returned an empty response", res.status);
  }

  return body as T;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const body = await requestJson<ApiResponse<T>>(path, init);

  if (!body.success) {
    const message = body.error?.message ?? "Request failed";
    throw new ApiRequestError(message, 200, body.error?.code);
  }
  return body.data as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  post: <T>(path: string, data: unknown) =>
    request<T>(path, { method: "POST", body: JSON.stringify(data) }),
  postRaw: <T>(path: string, data: unknown) =>
    requestJson<T>(path, { method: "POST", body: JSON.stringify(data) }),
  put: <T>(path: string, data: unknown) =>
    request<T>(path, { method: "PUT", body: JSON.stringify(data) }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};
