"use client";

import { useEffect, useRef, useState } from "react";
import { getToken } from "@/lib/session";
import { patientFailure, type PatientFailure } from "./logic";

/** Locks synchronously before the first await and ignores completions from a departed workspace/session. */
export function usePatientMutation(identity: string) {
  const generation = useRef(0);
  const locked = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<PatientFailure | null>(null);
  const [completed, setCompleted] = useState(false);
  const [uncertainError, setUncertainError] = useState<PatientFailure | null>(null);
  useEffect(() => {
    generation.current += 1;
    locked.current = false;
    return () => { generation.current += 1; };
  }, [identity]);

  async function run<T>(operation: () => Promise<T>, onSuccess?: (result: T) => void): Promise<T | undefined> {
    if (locked.current) return;
    locked.current = true;
    const version = generation.current;
    const token = getToken();
    const current = () => version === generation.current && token === getToken();
    setPending(true); setError(null);
    let unknown = false;
    let succeeded = false;
    try {
      const result = await operation();
      succeeded = true;
      if (current()) { setCompleted(true); onSuccess?.(result); }
      return current() ? result : undefined;
    } catch (cause: unknown) {
      if (current()) {
        const failure = patientFailure(cause, true);
        unknown = failure.outcomeUnknown;
        if (unknown) setUncertainError(failure);
        setError(failure);
      }
      return undefined;
    } finally {
      if (current()) {
        locked.current = unknown || succeeded;
        setPending(false);
      }
    }
  }

  return { run, pending, error, uncertainError, outcomeUnknown: Boolean(uncertainError),
    blocked: pending || completed || Boolean(uncertainError),
    clearError: () => setError(null) };
}
