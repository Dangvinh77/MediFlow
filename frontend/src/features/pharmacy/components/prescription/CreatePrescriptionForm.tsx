"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { useReferenceDirectory } from "@/components/references/ReferenceProvider";
import { PharmacyIdentity } from "../PharmacyIdentity";
import { PharmacySessionBoundary } from "../PharmacySessionBoundary";
import { Field, controlClassName } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import type { Role } from "@/lib/roles";
import { pharmacyApi } from "../../api";
import { hasUncertainMutationStatus } from "../../mutationOutcome";
import { getPharmacyCapabilities } from "../../permissions";
import type { DrugDTO, PrescribingContextDTO } from "../../types";
import { getLocalTodayIso, isUuid } from "../../utils";
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
import { PrescriptionRecordPicker } from "./PrescriptionRecordPicker";

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
  const directory = useReferenceDirectory();
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
  const [drugPage, setDrugPage] = useState(0);
  const [drugPageInfo, setDrugPageInfo] = useState({ totalPages: 0, totalElements: 0 });
  const [drugSearchError, setDrugSearchError] = useState<string | null>(null);
  const [settledDrugSearchKey, setSettledDrugSearchKey] = useState<string | null>(null);
  const [drugRetryToken, setDrugRetryToken] = useState(0);
  const requestSequence = useRef(0);
  const drugSearchKey = `${activeDrugKeyword}\u0000${drugPage}\u0000${drugRetryToken}`;
  const drugLoading = capabilities.canCreatePrescription && settledDrugSearchKey !== drugSearchKey;
  const today = getLocalTodayIso();
  const locked = submitting || uncertain || created;
  const [contextResult, setContextResult] = useState<{ recordId: string; data?: PrescribingContextDTO; error?: string } | null>(null);
  const [contextRetry, setContextRetry] = useState(0);
  const context = contextResult?.recordId === values.recordId ? contextResult : null;
  const [doctorResult, setDoctorResult] = useState<{ doctorId: string; departmentId?: string; label?: string; error?: string } | null>(null);
  const [doctorRetry, setDoctorRetry] = useState(0);
  const doctor = doctorResult?.doctorId === values.doctorId ? doctorResult : null;

  useEffect(() => {
    if (mode !== "legacy" || !isUuid(values.doctorId) || !capabilities.canCreatePrescription) return;
    let current = true;
    const doctorId = values.doctorId;
    pharmacyApi.getPrescribingDoctor(doctorId).then(async data => {
      if (!current || getSessionKey() !== actorToken) return;
      const departmentId = data.departmentId;
      if (data.staffId !== doctorId || data.jobTitle !== "DOCTOR" || !data.active || typeof departmentId !== "string" || !isUuid(departmentId)) {
        setDoctorResult({ doctorId, error: "Bác sĩ chưa có khoa hợp lệ hoặc không còn hoạt động. Hãy chọn lại bác sĩ." });
        return;
      }
      const label = await directory?.resolve("department", departmentId).catch(() => null);
      if (!current || getSessionKey() !== actorToken) return;
      setDoctorResult({ doctorId, departmentId, label: label || "Khoa của bác sĩ đã chọn" });
      setValues(previous => previous.doctorId === doctorId ? { ...previous, departmentId } : previous);
      setValidationErrors(previous => ({ ...previous, fields: { ...previous.fields, departmentId: undefined } }));
    }).catch((cause: unknown) => {
      if (current && getSessionKey() === actorToken) setDoctorResult({ doctorId, error: apiErrorMessage(cause, "Chưa lấy được khoa của bác sĩ. Hãy thử lại.") });
    });
    return () => { current = false; };
  }, [mode, values.doctorId, doctorRetry, directory, actorToken, capabilities.canCreatePrescription]);

  useEffect(() => {
    if (mode !== "outpatient" || !values.recordId || !capabilities.canCreatePrescription) return;
    let current = true;
    const recordId = values.recordId;
    const patientId = values.patientId;
    pharmacyApi.getPrescribingContext(recordId).then(data => {
      if (!current || getSessionKey() !== actorToken) return;
      if (data.recordId !== recordId || data.patientId !== patientId || data.careEpisodeType !== "OUTPATIENT_VISIT" || !data.priceCodes.length) {
        throw new Error("Hồ sơ chưa có đủ thông tin kê đơn hoặc không thuộc bệnh nhân đã chọn.");
      }
      setContextResult({ recordId, data });
      setValues(previous => previous.recordId === recordId && previous.patientId === patientId ? {
        ...previous, doctorId: data.doctorId, departmentId: data.departmentId, careEpisodeId: data.careEpisodeId,
        priceCode: data.priceCodes.length === 1 ? data.priceCodes[0] : "",
      } : previous);
    }).catch((cause: unknown) => {
      if (current && getSessionKey() === actorToken) setContextResult({ recordId, error: apiErrorMessage(cause, "Chưa lấy được thông tin từ hồ sơ khám. Hãy thử lại.") });
    });
    return () => { current = false; };
  }, [mode, values.recordId, values.patientId, contextRetry, actorToken, capabilities.canCreatePrescription]);

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
      .searchDrugs({ keyword: activeDrugKeyword, page: drugPage, size: 20 })
      .then((result) => {
        if (!active || requestId !== requestSequence.current) return;
        setDrugOptions(result.content);
        setDrugPageInfo({ totalPages: result.totalPages, totalElements: result.totalElements });
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
  }, [activeDrugKeyword, capabilities.canCreatePrescription, drugSearchKey, router, drugPage]);

  function updateField(field: keyof Omit<OutpatientPrescriptionFormValues, "lines">, value: string) {
    if (locked || values[field] === value) return;
    if (mode === "legacy" && field === "departmentId") return;
    if (field === "doctorId") setDoctorResult(null);
    if (mode === "outpatient" && (field === "patientId" || field === "recordId")) setContextResult(null);
    setValues((current) => ({ ...current, [field]: value,
      ...(field === "patientId" && value !== current.patientId ? { recordId: "", careEpisodeId: "" } : {}),
      ...(field === "departmentId" && value !== current.departmentId ? { doctorId: "" } : {}),
      ...(mode === "legacy" && field === "doctorId" ? { departmentId: "" } : {}),
      ...(mode === "outpatient" && (field === "patientId" || field === "recordId") ? { departmentId: "", doctorId: "", careEpisodeId: "", priceCode: "" } : {}),
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
    const field = (["patientId", "recordId", "doctorId", "departmentId", "prescribedDate", "careEpisodeId", "priceCode"] as const)
      .find(name => errors.fields[name] && (mode === "outpatient" || (name !== "careEpisodeId" && name !== "priceCode")));
    const lineKey = values.lines.find(line => Object.values(errors.lines[line.rowKey] ?? {}).some(Boolean))?.rowKey;
    const lineField = lineKey
      ? (["drugId", "quantity", "dosage"] as const).find(name => errors.lines[lineKey]?.[name])
      : undefined;
    focusTarget.current = field
      ? mode === "legacy" && field === "departmentId" ? "doctorId" : field
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
    setDrugPage(0);
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

    if (mode === "outpatient" && (!context?.data || context.error)) {
      setFormError("Hãy chọn hồ sơ khám và chờ hệ thống xác nhận thông tin kê đơn.");
      focusTarget.current = "recordId";
      return;
    }
    if (mode === "legacy" && (!doctor?.departmentId || doctor.departmentId !== values.departmentId)) {
      setFormError("Hãy chọn bác sĩ và chờ hệ thống xác nhận khoa phụ trách.");
      focusTarget.current = "doctorId";
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
        if (mode === "outpatient" && !mapped.fields.patientId && !mapped.fields.recordId && (mapped.fields.careEpisodeId || mapped.fields.priceCode || mapped.fields.doctorId || mapped.fields.departmentId)) {
          focusTarget.current = "recordId";
        } else focusFirstError(mapped);
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
          Chọn bệnh nhân, hồ sơ khám và bác sĩ phụ trách. Kiểm tra thông tin trước khi kê thuốc.
        </p>
        <div className="mt-5 grid gap-5 md:grid-cols-2">
          <Field label="Bệnh nhân" htmlFor="patientId" error={validationErrors.fields.patientId} required>
            <ReferenceInput kind="patient" id="patientId" {...field("patientId")} value={values.patientId} onValueChange={(id) => updateField("patientId", id)} className={fieldInputClass(Boolean(validationErrors.fields.patientId))} placeholder="Tìm bệnh nhân theo tên" />
            {!directory && <PharmacyIdentity kind="patient" id={values.patientId.trim()} />}
          </Field>
          <Field label="Hồ sơ khám" htmlFor="recordId" error={validationErrors.fields.recordId} required>
            <PrescriptionRecordPicker patientId={values.patientId} id="recordId" {...field("recordId")} value={values.recordId} onValueChange={(id) => updateField("recordId", id)} className={fieldInputClass(Boolean(validationErrors.fields.recordId))} />
          </Field>
          {mode === "legacy" && <>
          <Field label="Bác sĩ kê đơn" htmlFor="doctorId" error={validationErrors.fields.doctorId} required>
            <ReferenceInput kind="doctor" id="doctorId" {...field("doctorId")} value={values.doctorId} onValueChange={(id) => updateField("doctorId", id)} className={fieldInputClass(Boolean(validationErrors.fields.doctorId))} placeholder="Tìm bác sĩ theo tên" />
            {!directory && <PharmacyIdentity kind="doctor" id={values.doctorId.trim()} />}
          </Field>
          <Field label="Khoa khám" htmlFor="departmentId" error={validationErrors.fields.departmentId} required hint="Khoa được lấy theo bác sĩ, không chọn riêng.">
            <input id="departmentId" {...field("departmentId")} readOnly value={doctor?.label ?? ""} placeholder={!values.doctorId ? "Chọn bác sĩ trước" : doctor?.error ? "Chưa xác nhận được khoa" : !isUuid(values.doctorId) ? "Hãy chọn bác sĩ hợp lệ" : "Đang lấy khoa của bác sĩ…"} className={fieldInputClass(Boolean(validationErrors.fields.departmentId))} />
            {!directory && doctor?.departmentId && <PharmacyIdentity kind="department" id={doctor.departmentId} />}
            {doctor?.error && <div role="alert" className="text-sm text-danger">{doctor.error} <Button type="button" disabled={locked} onClick={() => { setDoctorResult(null); setDoctorRetry(value => value + 1); }}>Thử lấy khoa lại</Button></div>}
          </Field>
          </>}
          <Field label="Ngày kê đơn" htmlFor="prescribedDate" error={validationErrors.fields.prescribedDate} required>
            <input id="prescribedDate" type="date" max={today} {...field("prescribedDate")} value={values.prescribedDate} onChange={(event) => updateField("prescribedDate", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.prescribedDate))} />
          </Field>
          {mode === "outpatient" && <div className="rounded-lg border border-border bg-surface-muted p-4 text-sm md:col-span-2" aria-live="polite">
            {!values.recordId ? <p>Chọn hồ sơ khám. Hệ thống sẽ tự lấy bác sĩ, khoa và lượt khám liên quan.</p>
              : !context ? <p role="status">Đang xác nhận thông tin hồ sơ khám…</p>
              : context.error ? <div role="alert">{context.error} <Button type="button" variant="secondary" disabled={locked} onClick={() => { setContextResult(null); setContextRetry(value => value + 1); }}>Thử tải lại hồ sơ</Button></div>
              : <div className="space-y-3">
                <p className="font-medium text-success">Đã lấy thông tin kê đơn từ hồ sơ khám.</p>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div><p className="text-muted-foreground">Bác sĩ phụ trách</p><PharmacyIdentity kind="doctor" id={values.doctorId} /></div>
                  <div><p className="text-muted-foreground">Khoa khám</p><PharmacyIdentity kind="department" id={values.departmentId} /></div>
                </div>
                {context.data && context.data.priceCodes.length > 1 && <Field label="Nhóm thu tiền thuốc" htmlFor="priceCode" error={validationErrors.fields.priceCode} required>
                  <select id="priceCode" {...field("priceCode")} className={controlClassName} value={values.priceCode} onChange={event => updateField("priceCode", event.target.value)}>
                    <option value="">Chọn nhóm thu đã cấu hình</option>
                    {context.data.priceCodes.map(code => <option key={code} value={code}>{code}</option>)}
                  </select>
                </Field>}
                <p className="text-muted-foreground">Lượt khám và thông tin thu tiền đã được hệ thống cung cấp. Giá từng thuốc lấy từ danh mục thuốc.</p>
              </div>}
          </div>}
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
        drugPage={drugPage}
        drugPageInfo={drugPageInfo}
        onDrugPageChange={page => { if (!locked && !drugLoading) { setDrugSearchError(null); setDrugPage(page); } }}
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
          disabled={created || (mode === "outpatient" && !context?.data)}
          variant="primary"
        >
          {created ? "Đã tạo đơn thuốc" : uncertain ? "Thử gửi lại đơn" : "Tạo đơn thuốc"}
        </Button>
        {uncertain && <Button type="button" disabled={submitting} onClick={() => {
          focusTarget.current = "patientId";
          intent.current = null; setUncertain(false); setValues(createEmptyOutpatientPrescriptionForm());
          setValidationErrors({ fields: {}, lines: {} }); setFormError(null);
        }}>Đã kiểm tra đơn cũ, bắt đầu đơn mới</Button>}
        <Link href="/pharmacy/prescriptions" className="inline-flex min-h-11 items-center rounded-lg px-3 text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-primary">Tra cứu đơn thuốc</Link>
        <p className="text-sm text-muted-foreground">Giá chính thức được xác định khi tạo đơn.</p>
      </div>
    </form>
  );
}
