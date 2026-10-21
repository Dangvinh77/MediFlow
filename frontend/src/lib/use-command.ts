"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "./api";
import type { ApiError } from "./types";
import { getSessionKey } from "./session";

export interface CommandError {
  status: number | null;
  message: string;
  code?: string;
  correlationId: string | null;
  details: ApiError["details"];
}

// A synchronous lock closes the gap before React renders disabled buttons.
// Late responses belong only to the mounted workspace that initiated them.
export function useCommand() {
  const router = useRouter();
  const mounted = useRef(false);
  const locked = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<CommandError | null>(null);
  const [uncertain, setUncertain] = useState(false);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  async function run<T>(command: () => Promise<T>, onSuccess: (result: T) => void) {
    if (locked.current || !mounted.current) return;
    locked.current = true;
    setBusy(true);
    setError(null);
    setUncertain(false);
    let unknownOutcome = false;
    const token = getSessionKey();
    const isCurrent = () => mounted.current && getSessionKey() === token;
    try {
      const result = await command();
      if (isCurrent()) onSuccess(result);
    } catch (cause: unknown) {
      if (!isCurrent()) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      // Empty/malformed 2xx payloads can follow a committed write too. Only a
      // definitive 4xx rejection establishes that this intent was rejected.
      unknownOutcome = !(cause instanceof ApiRequestError) || cause.status < 400
        || cause.status === 408 || cause.status >= 500;
      setUncertain(unknownOutcome);
      setError(cause instanceof ApiRequestError
        ? { status: cause.status, message: cause.message, code: cause.code, details: cause.details, correlationId: cause.correlationId }
        : { status: null, message: cause instanceof Error ? cause.message : "Không thể xử lý yêu cầu.", details: [], correlationId: null });
    } finally {
      // Reconciliation must remount the workspace before another mutation.
      // Keep this guard synchronous even when a consumer misses its disabled state.
      locked.current = unknownOutcome;
      if (mounted.current) setBusy(false);
    }
  }
  return { run, busy, error, uncertain };
}
