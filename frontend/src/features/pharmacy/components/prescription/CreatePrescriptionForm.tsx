"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { PharmacyIdentity } from "../PharmacyIdentity";
import { PharmacySessionBoundary } from "../PharmacySessionBoundary";
import { Field, controlClassName } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import type { Role } from "@/lib/roles";
import { pharmacyApi } from "../../api";
import { hasUncertainMutationStatus } from "../../mutationOutcome";
import { getPharmacyCapabilities } from "../../permissions";
import type { DrugDTO } from "../../types";
import { getLocalTodayIso } from "../../utils";
import {
  createPrescriptionLine,
  hasCreatePrescriptionErrors,
  toCreatePrescriptionRequest,
  validateCreatePrescriptionForm,
  type CreatePrescriptionFormValues,
  type CreatePrescriptionValidationErrors,
} from "./prescriptionFormValidation";
import { createEmptyOutpatientPrescriptionForm, toOutpatientPrescriptionRequest, validateOutpatientPrescriptionForm,
  type OutpatientPrescriptionFormValues, type OutpatientPrescriptionValidationErrors } from "./outpatientPrescriptionForm";
import { prepareOutpatientPrescriptionIntent, type OutpatientPrescriptionIntent } from "../../outpatientPrescriptionIntent";
import { PrescriptionLinesEditor } from "./PrescriptionLinesEditor";

function getServerRole(): Role | null {
  return null;
}

function correlationSuffix(cause: ApiRequestError): string {
  return cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
}

function apiErrorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof ApiRequestError) {
    if (cause.code === "PRESCRIPTION_CREATION_FORBIDDEN") {
      return `Danh tính đăng nhập chưa được xác nhận là bác sĩ kê đơn này. Hãy kiểm tra mã bác sĩ hoặc liên hệ quản trị viên.${correlationSuffix(cause)}`;
    }
    if (cause.status === 403) return `Bạn không có quyền thực hiện thao tác này.${correlationSuffix(cause)}`;
    return `${cause.message}${correlationSuffix(cause)}`;
  }
  return fallback;
}

function fieldInputClass(invalid: boolean): string {
  return `${controlClassName}${invalid ? " border-danger" : ""}`;
}

function mapPrescriptionValidationDetails(
  details: { field: string; message: string }[],
  lines: CreatePrescriptionFormValues["lines"],
): OutpatientPrescriptionValidationErrors {
  const fields: OutpatientPrescriptionValidationErrors["fields"] = {};
  const lineErrors: Record<string, CreatePrescriptionValidationErrors["lines"][string]> = {};
  const lineFieldPattern = /^lines(?:\[(\d+)\]|\.(\d+))\.(drugId|quantity|dosage)$/;

  for (const detail of details) {
    const match = lineFieldPattern.exec(detail.field);
    if (match) {
      const lineIndex = Number(match[1] ?? match[2]);
      const row = lines[lineIndex];
      if (row) {
        const field = match[3] as keyof CreatePrescriptionValidationErrors["lines"][string];
        lineErrors[row.rowKey] = {
          ...lineErrors[row.rowKey],
          [field]: detail.message,
        };
      }
      continue;
    }

    if (["recordId", "patientId", "doctorId", "departmentId", "prescribedDate", "careEpisodeId", "priceCode"].includes(detail.field)) {
      fields[detail.field as keyof typeof fields] = detail.message;
    }
  }

  return { fields, lines: lineErrors };
}

export function CreatePrescriptionForm({ mode = "outpatient" }: { mode?: "outpatient" | "legacy" }) {
  const actorToken = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  // A different session or deployment mode starts a fresh draft and command.
  return <PharmacySessionBoundary><PrescriptionForm key={`${mode}:${actorToken}`} mode={mode} actorToken={actorToken} /></PharmacySessionBoundary>;
}

function PrescriptionForm({ mode, actorToken }: { mode: "outpatient" | "legacy"; actorToken: string | null }) {
  const router = useRouter();
  const role = useSyncExternalStore(
    subscribeToAuthChanges,
    getRole,
    getServerRole,
  );
  const capabilities = getPharmacyCapabilities(role);
  const [values, setValues] = useState<OutpatientPrescriptionFormValues>(createEmptyOutpatientPrescriptionForm);
  const [validationErrors, setValidationErrors] = useState<OutpatientPrescriptionValidationErrors>({ fields: {}, lines: {} });
  const intent = useRef<OutpatientPrescriptionIntent | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const completed = useRef(false);
  const [created, setCreated] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const active = useRef(true);
  const focusTarget = useRef<string | undefined>(undefined);
  const feedbackRef = useRef<HTMLDivElement>(null);
  const [drugKeyword, setDrugKeyword] = useState("");
  const [activeDrugKeyword, setActiveDrugKeyword] = useState("");
  const [drugOptions, setDrugOptions] = useState<DrugDTO[]>([]);
  const [drugSearchError, setDrugSearchError] = useState<string | null>(null);
  const [settledDrugSearchKey, setSettledDrugSearchKey] = useState<string | null>(null);
  const [drugRetryToken, setDrugRetryToken] = useState(0);
  const requestSequence = useRef(0);
  const drugSearchKey = `${activeDrugKeyword}\u0000${drugRetryToken}`;
  const drugLoading = capabilities.canCreatePrescription && settledDrugSearchKey !== drugSearchKey;
  const today = getLocalTodayIso();
  const locked = submitting || uncertain || created;

  useEffect(() => {
    active.current = true;
    return () => { active.current = false; intent.current = null; };
  }, []);

  useEffect(() => {
    if (submitting) return;
    if (focusTarget.current && !locked) document.getElementById(focusTarget.current)?.focus();
    else if (formError) feedbackRef.current?.focus();
    focusTarget.current = undefined;
  }, [validationErrors, submitting, values.lines, formError, locked]);

  useEffect(() => {
    if (!capabilities.canCreatePrescription) return undefined;

    let active = true;
    const requestId = ++requestSequence.current;

    pharmacyApi
      .searchDrugs({ keyword: activeDrugKeyword, page: 0, size: 20 })
      .then((result) => {
        if (!active || requestId !== requestSequence.current) return;
        setDrugOptions(result.content);
      })
      .catch((cause: unknown) => {
        if (!active || requestId !== requestSequence.current) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }
        setDrugSearchError(apiErrorMessage(cause, "Không thể tải danh mục thuốc."));
      })
      .finally(() => {
        if (active && requestId === requestSequence.current) {
          setSettledDrugSearchKey(drugSearchKey);
        }
      });

    return () => {
      active = false;
    };
  }, [activeDrugKeyword, capabilities.canCreatePrescription, drugSearchKey, router]);

  function updateField(field: keyof Omit<OutpatientPrescriptionFormValues, "lines">, value: string) {
    if (locked) return;
    setValues((current) => ({ ...current, [field]: value,
      ...(field === "patientId" && value !== current.patientId ? { recordId: "", careEpisodeId: "" } : {}),
      ...(field === "departmentId" && value !== current.departmentId ? { doctorId: "" } : {}),
    }));
    setValidationErrors((current) => ({
      ...current,
      fields: { ...current.fields, [field]: undefined },
    }));
    setFormError(null);
  }

  function updateLine(rowKey: string, patch: Partial<CreatePrescriptionFormValues["lines"][number]>) {
    if (locked) return;
    setValues((current) => ({
      ...current,
      lines: current.lines.map((line) => (line.rowKey === rowKey ? { ...line, ...patch } : line)),
    }));
    setValidationErrors((current) => {
      const nextLines = { ...current.lines };
      delete nextLines[rowKey];
      return { ...current, lines: nextLines };
    });
    setFormError(null);
  }

  function focusFirstError(errors: OutpatientPrescriptionValidationErrors) {
    // Backend detail order is not the visual or keyboard order of the form.
    const field = (["recordId", "patientId", "doctorId", "departmentId", "prescribedDate", "careEpisodeId", "priceCode"] as const)
      .find(name => errors.fields[name] && (mode === "outpatient" || (name !== "careEpisodeId" && name !== "priceCode")));
    const lineKey = values.lines.find(line => Object.values(errors.lines[line.rowKey] ?? {}).some(Boolean))?.rowKey;
    const lineField = lineKey
      ? (["drugId", "quantity", "dosage"] as const).find(name => errors.lines[lineKey]?.[name])
      : undefined;
    focusTarget.current = field
      ? field
      : lineKey && lineField
        ? `${lineField}-${lineKey}`
        : errors.lineList ? "prescription-add-line" : undefined;
  }

  function addLine() {
    if (locked) return;
    const line = createPrescriptionLine();
    focusTarget.current = `drugId-${line.rowKey}`;
    setValues(current => ({ ...current, lines: [...current.lines, line] }));
    setValidationErrors(current => ({ ...current, lineList: undefined }));
  }

  function removeLine(rowKey: string) {
    if (locked || values.lines.length <= 1) return;
    const index = values.lines.findIndex(line => line.rowKey === rowKey);
    if (index < 0) return;
    const next = values.lines[index + 1] ?? values.lines[index - 1];
    focusTarget.current = `drugId-${next.rowKey}`;
    setValues(current => ({ ...current, lines: current.lines.filter(line => line.rowKey !== rowKey) }));
    setValidationErrors(current => {
      const lines = { ...current.lines };
      delete lines[rowKey];
      return { ...current, lines, lineList: undefined };
    });
  }

  function submitDrugSearch() {
    setDrugSearchError(null);
    setActiveDrugKeyword(drugKeyword.trim());
    setDrugRetryToken((value) => value + 1);
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current || completed.current || getSessionKey() !== actorToken || !getPharmacyCapabilities(getRole()).canCreatePrescription) return;

    setFormError(null);
    const errors = mode === "outpatient" ? validateOutpatientPrescriptionForm(values, today) : validateCreatePrescriptionForm(values, today);
    setValidationErrors(errors);
    if (hasCreatePrescriptionErrors(errors)) {
      focusFirstError(errors);
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    setUncertain(false);
    try {
      let created;
      if (mode === "outpatient") {
        intent.current = prepareOutpatientPrescriptionIntent(toOutpatientPrescriptionRequest(values, today), intent.current);
        created = await pharmacyApi.createOutpatientPrescription(intent.current);
      } else {
        created = await pharmacyApi.createPrescription(toCreatePrescriptionRequest(values));
      }
      if (!active.current || getSessionKey() !== actorToken) return;
      intent.current = null;
      setUncertain(false);
      completed.current = true;
      setCreated(true);
      router.replace(`/pharmacy/prescriptions/${created.prescriptionId}`);
    } catch (cause: unknown) {
      if (!active.current || getSessionKey() !== actorToken) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      const unknownOutcome = !(cause instanceof ApiRequestError) || hasUncertainMutationStatus(cause)
        || (cause.status === 409 && cause.code !== "PRESCRIPTION_DUPLICATE_DRUG" && cause.code !== "INSUFFICIENT_AVAILABLE_STOCK");
      if (unknownOutcome) {
        setUncertain(mode === "outpatient");
        setFormError("Chưa xác định được kết quả tạo đơn. Hãy kiểm tra đơn đã tạo trước khi gửi lại để tránh tạo trùng."
          + (mode === "outpatient" ? " Thử gửi lại sẽ dùng nguyên nội dung và mã yêu cầu trước đó." : "")
          + (cause instanceof ApiRequestError ? correlationSuffix(cause) : ""));
        return;
      }

      if (cause instanceof ApiRequestError && cause.details.length > 0) {
        const mapped = mapPrescriptionValidationDetails(cause.details, values.lines);
        setValidationErrors((current) => ({
          fields: { ...current.fields, ...mapped.fields },
          lines: { ...current.lines, ...mapped.lines },
        }));
        focusFirstError(mapped);
      }

      if (cause instanceof ApiRequestError && cause.code === "PRESCRIPTION_DUPLICATE_DRUG") {
        setFormError(`Đơn thuốc không được chứa thuốc trùng nhau. Hãy kiểm tra các dòng thuốc.${correlationSuffix(cause)}`);
      } else if (cause instanceof ApiRequestError && cause.code === "INSUFFICIENT_AVAILABLE_STOCK") {
        setFormError(`Một hoặc nhiều thuốc không đủ tồn khả dụng. Hãy kiểm tra lại tồn kho rồi thử lại.${correlationSuffix(cause)}`);
      } else {
        setFormError(apiErrorMessage(cause, "Không thể tạo đơn thuốc."));
      }
    } finally {
      submittingRef.current = false;
      if (active.current) setSubmitting(false);
    }
  }

  if (role === null) {
    return <p className="mt-6 text-sm text-muted-foreground">Đang kiểm tra quyền…</p>;
  }

  if (!capabilities.canCreatePrescription) {
    return (
      <p role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
        Tài khoản của bạn không có quyền tạo đơn thuốc.
      </p>
    );
  }

  const field = (name: keyof OutpatientPrescriptionValidationErrors["fields"]) => ({
    disabled: locked,
    "aria-required": true,
    "aria-invalid": validationErrors.fields[name] ? true : undefined,
    "aria-describedby": validationErrors.fields[name] ? `${name}-error` : undefined,
  });

  return (
    <form onSubmit={submit} noValidate aria-busy={submitting || undefined} className="mt-6 space-y-6">
      {formError && (
        <div ref={feedbackRef} role="alert" tabIndex={-1} className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger">
          {formError}
        </div>
      )}

      <section className="rounded-xl border border-border bg-surface p-5 sm:p-6" aria-labelledby="prescription-context-title">
        <h2 id="prescription-context-title" className="text-base font-semibold">Thông tin kê đơn</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {mode === "outpatient" ? "Chọn bệnh nhân, khoa và bác sĩ theo tên. Mã hồ sơ, lượt khám và giá thuốc phải được xác nhận từ nghiệp vụ nguồn." : "Nhập mã từ hồ sơ khám của bệnh nhân và thông tin bác sĩ phụ trách."}
        </p>
        <div className="mt-5 grid gap-5 md:grid-cols-2">
          <Field label="Mã hồ sơ bệnh án" htmlFor="recordId" error={validationErrors.fields.recordId} required>
            <input id="recordId" {...field("recordId")} value={values.recordId} onChange={(event) => updateField("recordId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.recordId))} placeholder="UUID hồ sơ" />
          </Field>
          <Field label="Mã bệnh nhân" htmlFor="patientId" error={validationErrors.fields.patientId} required>
            <ReferenceInput kind="patient" id="patientId" {...field("patientId")} value={values.patientId} onValueChange={(id) => updateField("patientId", id)} className={fieldInputClass(Boolean(validationErrors.fields.patientId))} placeholder="UUID bệnh nhân" />
            <PharmacyIdentity kind="patient" id={values.patientId.trim()} showId={false} />
          </Field>
          <Field label="Mã bác sĩ" htmlFor="doctorId" error={validationErrors.fields.doctorId} required>
            <ReferenceInput kind="doctor" scope={values.departmentId} id="doctorId" {...field("doctorId")} value={values.doctorId} onValueChange={(id) => updateField("doctorId", id)} className={fieldInputClass(Boolean(validationErrors.fields.doctorId))} placeholder="UUID bác sĩ" />
            <PharmacyIdentity kind="doctor" id={values.doctorId.trim()} showId={false} />
          </Field>
          <Field label="Mã khoa" htmlFor="departmentId" error={validationErrors.fields.departmentId} required>
            <ReferenceInput kind="department" id="departmentId" {...field("departmentId")} value={values.departmentId} onValueChange={(id) => updateField("departmentId", id)} className={fieldInputClass(Boolean(validationErrors.fields.departmentId))} placeholder="UUID khoa" />
            <PharmacyIdentity kind="department" id={values.departmentId.trim()} showId={false} />
          </Field>
          <Field label="Ngày kê đơn" htmlFor="prescribedDate" error={validationErrors.fields.prescribedDate} required>
            <input id="prescribedDate" type="date" max={today} {...field("prescribedDate")} value={values.prescribedDate} onChange={(event) => updateField("prescribedDate", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.prescribedDate))} />
          </Field>
          {mode === "outpatient" && <>
            <Field label="Mã lượt khám ngoại trú" htmlFor="careEpisodeId" error={validationErrors.fields.careEpisodeId} required>
              <input id="careEpisodeId" {...field("careEpisodeId")} value={values.careEpisodeId} onChange={event => updateField("careEpisodeId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.careEpisodeId))} placeholder="UUID lượt khám đã xác nhận" />
            </Field>
            <Field label="Mã giá thuốc" htmlFor="priceCode" error={validationErrors.fields.priceCode} required hint="Dùng đúng mã trong danh mục giá được cấp cho lượt khám.">
              <input id="priceCode" {...field("priceCode")} maxLength={64} value={values.priceCode} onChange={event => updateField("priceCode", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.priceCode))} />
            </Field>
          </>}
        </div>
      </section>

      <PrescriptionLinesEditor
        lines={values.lines}
        disabled={locked}
        listError={validationErrors.lineList}
        errors={validationErrors.lines}
        drugOptions={drugLoading || drugSearchError ? [] : drugOptions}
        drugKeyword={drugKeyword}
        drugLoading={drugLoading}
        drugError={drugSearchError}
        onDrugKeywordChange={setDrugKeyword}
        onSearchDrugs={submitDrugSearch}
        onChange={updateLine}
        onAdd={addLine}
        onRemove={removeLine}
      />

      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="submit"
          loading={submitting}
          disabled={created}
          variant="primary"
        >
          {created ? "Đã tạo đơn thuốc" : uncertain ? "Thử gửi lại đơn" : "Tạo đơn thuốc"}
        </Button>
        {uncertain && <Button type="button" disabled={submitting} onClick={() => {
          focusTarget.current = "recordId";
          intent.current = null; setUncertain(false); setValues(createEmptyOutpatientPrescriptionForm());
          setValidationErrors({ fields: {}, lines: {} }); setFormError(null);
        }}>Đã kiểm tra đơn cũ, bắt đầu đơn mới</Button>}
        <Link href="/pharmacy/prescriptions" className="inline-flex min-h-11 items-center rounded-lg px-3 text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-primary">Tra cứu đơn thuốc</Link>
        <p className="text-sm text-muted-foreground">Giá chính thức được xác định khi tạo đơn.</p>
      </div>
    </form>
  );
}
