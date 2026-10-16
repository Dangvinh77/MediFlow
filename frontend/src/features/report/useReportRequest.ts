"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";

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

  const execute = useCallback((request: () => Promise<T>, requestId: number) => {
    // Also captures synchronous facade errors without allowing an obsolete request to commit.
    void Promise.resolve().then(request).then((data) => {
      if (mounted.current && sequence.current === requestId) setState({ status: "success", data });
    }).catch((cause: unknown) => {
      if (!mounted.current || sequence.current !== requestId) return;
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
  }, [router]);

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
    lastRequest.current = request;
    setState({ status: "loading" });
    execute(request, ++sequence.current);
  }, [execute]);

  const retry = useCallback(() => load(lastRequest.current), [load]);
  return { state, load, retry };
}
