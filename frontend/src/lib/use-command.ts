"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "./api";
import type { ApiError } from "./types";

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
    try {
      const result = await command();
      if (mounted.current) onSuccess(result);
    } catch (cause: unknown) {
      if (!mounted.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      // Empty/malformed 2xx payloads can follow a committed write too. Only a
      // definitive 4xx rejection establishes that this intent was rejected.
      setUncertain(!(cause instanceof ApiRequestError) || cause.status < 400 || cause.status >= 500);
      setError(cause instanceof ApiRequestError
        ? { status: cause.status, message: cause.message, code: cause.code, details: cause.details, correlationId: cause.correlationId }
        : { status: null, message: cause instanceof Error ? cause.message : "Không thể xử lý yêu cầu.", details: [], correlationId: null });
    } finally {
      locked.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  return { run, busy, error, uncertain };
}
