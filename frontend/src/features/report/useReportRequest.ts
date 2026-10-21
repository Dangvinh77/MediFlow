"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/auth";
import { getSessionKey } from "@/lib/session";
import { canReadReports } from "./components/ReportSessionBoundary";

export type ReportRequestState<T> =
  | { status: "idle" | "loading" }
  | { status: "success"; data: T }
  | { status: "error"; message: string; code?: string; correlationId: string | null; forbidden: boolean };

/** Local read lifecycle: no cache, transport or cross-feature data ownership. */
export function useReportRequest<T>(initialRequest: () => Promise<T>, autoLoad: boolean) {
  const router = useRouter();
  const [state, setState] = useState<ReportRequestState<T>>({ status: autoLoad ? "loading" : "idle" });
  const sequence = useRef(0);
  const mounted = useRef(false);
  const lastRequest = useRef(initialRequest);
  const session = useRef({ token: getSessionKey(), role: getRole() });
  const isCurrent = useCallback(() => mounted.current && canReadReports()
    && getSessionKey() === session.current.token && getRole() === session.current.role, []);

  const execute = useCallback((request: () => Promise<T>, requestId: number) => {
    // Also captures synchronous facade errors without allowing an obsolete request to commit.
    void Promise.resolve().then(() => isCurrent() && sequence.current === requestId ? request() : undefined).then((data) => {
      if (isCurrent() && sequence.current === requestId && data !== undefined) setState({ status: "success", data });
    }).catch((cause: unknown) => {
      if (!isCurrent() || sequence.current !== requestId) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      setState({
        status: "error",
        message: cause instanceof Error ? cause.message : "Không thể tải báo cáo.",
        code: cause instanceof ApiRequestError ? cause.code : undefined,
        correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
        forbidden: cause instanceof ApiRequestError && cause.status === 403,
      });
    });
  }, [isCurrent, router]);

  const invalidate = useCallback(() => {
    mounted.current = false;
    sequence.current++;
  }, []);

  useEffect(() => {
    mounted.current = true;
    lastRequest.current = initialRequest;
    if (autoLoad) execute(initialRequest, ++sequence.current);
    return invalidate;
  }, [autoLoad, execute, initialRequest, invalidate]);

  const load = useCallback((request: () => Promise<T>) => {
    if (!isCurrent()) return;
    lastRequest.current = request;
    setState({ status: "loading" });
    execute(request, ++sequence.current);
  }, [execute, isCurrent]);

  const retry = useCallback(() => load(lastRequest.current), [load]);
  const clear = useCallback(() => {
    sequence.current++;
    setState({ status: "idle" });
  }, []);
  return { state, load, retry, clear };
}
