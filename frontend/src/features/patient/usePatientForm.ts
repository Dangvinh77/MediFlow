"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getRole } from "@/lib/auth";
import { getSessionKey } from "@/lib/session";
import { patientApi } from "./api";
import { emptyPatientDraft, patientCreateBody, patientFailure, patientPermissions,
  patientToDraft, patientUpdateBody, validatePatientDraft,
  type PatientDraft, type PatientFailure, type PatientFieldErrors } from "./logic";
import { usePatientMutation } from "./usePatientMutation";

/** One mounted workspace per patient and session. No markup, storage or automatic mutation retries. */
export function usePatientForm(patientId?: string, onSaved?: () => void) {
  const router = useRouter();
  const [form, setForm] = useState<PatientDraft>(emptyPatientDraft);
  const [loading, setLoading] = useState(Boolean(patientId));
  const [loaded, setLoaded] = useState(!patientId);
  const [loadError, setLoadError] = useState<PatientFailure | null>(null);
  const [fieldErrors, setFieldErrors] = useState<PatientFieldErrors>({});
  const [validationError, setValidationError] = useState<PatientFailure | null>(null);
  const [revision, setRevision] = useState(0);
  const mutation = usePatientMutation(patientId ?? "create");

  useEffect(() => {
    if (!patientId) return;
    let active = true;
    const token = getSessionKey();
    const current = () => active && token === getSessionKey();
    patientApi.getById(patientId).then((patient) => {
      if (!current()) return;
      setForm(patientToDraft(patient)); setLoaded(true);
    }).catch((cause: unknown) => {
      if (!current()) return;
      setLoadError(patientFailure(cause));
    }).finally(() => { if (current()) setLoading(false); });
    return () => { active = false; };
  }, [patientId, revision]);

  const error = validationError ?? mutation.error ?? loadError;
  useEffect(() => { if (error?.status === 401) router.replace("/login"); }, [error, router]);

  function set<K extends keyof PatientDraft>(key: K, value: PatientDraft[K]) {
    if (mutation.blocked) return;
    setForm((current) => ({ ...current, [key]: value }));
    setFieldErrors((current) => ({ ...current, [key]: undefined }));
    setValidationError(null);
  }

  function retry() {
    if (!patientId || loading || mutation.blocked) return;
    setLoading(true); setLoadError(null); setRevision((value) => value + 1);
  }

  async function save() {
    if (!loaded || loading || mutation.blocked) return;
    if (!patientPermissions(getRole()).write) {
      setValidationError(patientFailure(new Error("Bạn không có quyền lưu bệnh nhân."))); return;
    }
    const errors = validatePatientDraft(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length) {
      setValidationError(patientFailure(new Error(Object.values(errors)[0]))); return;
    }
    setValidationError(null);
    await mutation.run(() => patientId
      ? patientApi.update(patientId, patientUpdateBody(form))
      : patientApi.create(patientCreateBody(form)),
      (patient) => { onSaved?.(); router.push(`/patients/${encodeURIComponent(patient.maBenhNhan)}?notice=${patientId ? "updated" : "created"}`); });
  }

  return { form, set, loading, loaded, saving: mutation.pending, blocked: mutation.blocked,
    outcomeUnknown: mutation.outcomeUnknown,
    error, fieldErrors: { ...mutation.error?.fieldErrors, ...fieldErrors }, save, retry };
}
