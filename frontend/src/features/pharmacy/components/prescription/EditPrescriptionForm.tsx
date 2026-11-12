"use client";

import { useEffect, useRef, useState } from "react";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/auth";
import { pharmacyApi } from "../../api";
import { hasUncertainMutationStatus } from "../../mutationOutcome";
import { canEditPrescription } from "../../permissions";
import type { DrugDTO, PrescriptionDTO, UpdatePrescriptionRequest } from "../../types";
import { usePharmacyRequestScope } from "../PharmacySessionBoundary";
import { PrescriptionLinesEditor } from "./PrescriptionLinesEditor";
import { createPrescriptionLine, toCreatePrescriptionRequest, validateCreatePrescriptionForm,
  type CreatePrescriptionValidationErrors, type PrescriptionLineFormValue } from "./prescriptionFormValidation";

interface Props {
  prescription: PrescriptionDTO;
  onSaved: (value: PrescriptionDTO) => void;
  onClose: () => void;
  onConflict: (message: string) => void;
}

function message(cause: unknown): string {
  if (cause instanceof ApiRequestError && ["BILLING_PRESCRIPTION_REVISION_PAYMENT_EXISTS", "BILLING_PRESCRIPTION_REVISION_PAYMENT_IN_PROGRESS", "PRESCRIPTION_ALREADY_PAID"].includes(cause.code ?? "")) {
    return `Đơn thuốc đã có giao dịch hoặc đang được thu tiền. Hãy làm mới để kiểm tra.${cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : ""}`;
  }
  return cause instanceof ApiRequestError
    ? `${cause.message}${cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : ""}`
    : "Chưa xác định được kết quả sửa đơn thuốc. Hãy gửi lại đúng lần sửa để kiểm tra.";
}

/** One revision-bound draft; unknown replies retain the same payload and command identity. */
export function EditPrescriptionForm({ prescription, onSaved, onClose, onConflict }: Props) {
  const { localize } = useLocale();
  const isCurrent = usePharmacyRequestScope("canCreatePrescription");
  const [lines, setLines] = useState<PrescriptionLineFormValue[]>(() => prescription.lines.map(line => ({
    ...createPrescriptionLine(), drugId: line.drugId, quantity: String(line.quantity),
    dosage: line.dosage ?? "", note: line.note ?? "",
  })));
  const [initial, setInitial] = useState<{ ready: boolean; error?: string }>({ ready: false });
  const [initialRetry, setInitialRetry] = useState(0);
  const [errors, setErrors] = useState<CreatePrescriptionValidationErrors>({ fields: {}, lines: {} });
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState({ keyword: "", page: 0, retry: 0 });
  const [catalog, setCatalog] = useState<{ key: string; drugs: DrugDTO[]; totalPages: number; totalElements: number; error: string | null } | null>(null);
  const searchKey = JSON.stringify(search);
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sending = useRef(false);
  const completed = useRef(false);
  const intent = useRef<{ key: string; body: UpdatePrescriptionRequest } | null>(null);
  const mounted = useRef(true);
  const locked = busy || uncertain;

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let current = true;
    Promise.all(prescription.lines.map(line => pharmacyApi.getDrug(line.drugId))).then(drugs => {
      if (!current || !isCurrent()) return;
      if (drugs.some((drug, i) => drug.drugId !== prescription.lines[i].drugId)) throw new Error("Danh mục thuốc không khớp đơn đang sửa.");
      setLines(previous => previous.map(line => ({ ...line, drug: drugs.find(drug => drug.drugId === line.drugId) ?? null })));
      setInitial({ ready: true });
    }).catch(cause => {
      if (current && isCurrent()) setInitial({ ready: false, error: cause instanceof Error ? cause.message : "Không thể tải danh mục thuốc." });
    });
    return () => { current = false; };
  }, [prescription, initialRetry, isCurrent]);

  useEffect(() => {
    let current = true;
    pharmacyApi.searchDrugs({ keyword: search.keyword, page: search.page, size: 20 }).then(result => {
      if (current && isCurrent()) setCatalog({ key: searchKey, drugs: result.content, totalPages: result.totalPages,
        totalElements: result.totalElements, error: null });
    }).catch(cause => {
      if (current && isCurrent()) setCatalog({ key: searchKey, drugs: [], totalPages: 0, totalElements: 0,
        error: cause instanceof ApiRequestError ? message(cause) : "Không thể tải danh mục thuốc." });
    });
    return () => { current = false; };
  }, [search, searchKey, isCurrent]);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (sending.current || completed.current || !initial.ready || !isCurrent() || !canEditPrescription(getRole(), prescription)) return;
    if (!intent.current) {
      const values = { recordId: prescription.recordId ?? "", patientId: prescription.patientId,
        doctorId: prescription.doctorId, departmentId: prescription.departmentId, prescribedDate: prescription.prescribedDate, lines };
      const validation = validateCreatePrescriptionForm(values);
      setErrors(validation);
      // Source identities and date are immutable here; only validate the editable lines.
      if (validation.lineList || Object.keys(validation.lines).length) return;
      intent.current = { key: crypto.randomUUID(), body: {
        expectedRevision: prescription.revision!, lines: toCreatePrescriptionRequest(values).lines,
      } };
    }
    sending.current = true; setBusy(true); setError(null);
    try {
      const updated = await pharmacyApi.updatePrescription(prescription.prescriptionId, intent.current.body, intent.current.key);
      if (!mounted.current || !isCurrent()) return;
      if (updated.prescriptionId !== prescription.prescriptionId || !Number.isSafeInteger(updated.revision)
        || updated.revision! <= prescription.revision!) {
        throw new Error("Chưa xác định được kết quả sửa đơn thuốc. Hãy gửi lại đúng lần sửa để kiểm tra.");
      }
      completed.current = true; onSaved(updated);
    } catch (cause) {
      if (!mounted.current || !isCurrent()) return;
      if (!(cause instanceof ApiRequestError) || hasUncertainMutationStatus(cause) || cause.status === 429) {
        setUncertain(true); setError(message(cause));
      } else if ([401, 403, 404, 409].includes(cause.status)
        || cause.code?.startsWith("BILLING_PRESCRIPTION_REVISION_")
        || ["PRESCRIPTION_ALREADY_PAID", "PRESCRIPTION_NOT_EDITABLE", "PRESCRIPTION_DISPENSE_NOT_PENDING", "PHARMACY_OUTPATIENT_V1_REQUIRED"].includes(cause.code ?? "")) {
        completed.current = true; onConflict(message(cause));
      } else {
        intent.current = null; setUncertain(false); setError(message(cause));
      }
    } finally {
      sending.current = false;
      if (mounted.current && isCurrent()) setBusy(false);
    }
  }

  if (!initial.ready) return <div className="mt-4 space-y-3">
    {initial.error
      ? <AsyncState kind="error" message={initial.error} onRetry={() => setInitialRetry(value => value + 1)} />
      : <AsyncState kind="loading" message={localize("Đang tải danh mục thuốc…")} />}
    <Button type="button" onClick={onClose}><L10n>Đóng</L10n></Button>
  </div>;

  return <form aria-label={localize("Sửa đơn thuốc")} onSubmit={save} noValidate className="mt-5 space-y-4">
    <p className="text-sm text-muted-foreground"><L10n>Chỉ sửa khi chưa thanh toán. Hệ thống kiểm tra lại quyền, tồn kho và khoản phải thu khi lưu.</L10n></p>
    {error && <p role="alert" className="rounded-lg border border-danger/40 p-3 text-sm text-danger"><L10nValue>{error}</L10nValue></p>}
    <PrescriptionLinesEditor lines={lines} disabled={locked} errors={errors.lines} listError={errors.lineList}
      drugOptions={catalog?.key === searchKey ? catalog.drugs : []} drugKeyword={keyword}
      drugLoading={catalog?.key !== searchKey} drugError={catalog?.key === searchKey ? catalog.error : null}
      drugPage={search.page} drugPageInfo={catalog ?? { totalPages: 0, totalElements: 0 }}
      onDrugPageChange={page => { if (!locked) setSearch(previous => ({ ...previous, page })); }}
      onDrugKeywordChange={value => { if (!locked) setKeyword(value); }}
      onSearchDrugs={() => { if (!locked) setSearch(previous => ({ keyword: keyword.trim(), page: 0, retry: previous.retry + 1 })); }}
      onChange={(key, patch) => { if (!locked) setLines(previous => previous.map(line => line.rowKey === key ? { ...line, ...patch } : line)); }}
      onAdd={drug => {
        if (locked || lines.some(line => line.drugId === drug.drugId)) return null;
        const line = createPrescriptionLine(drug); setLines(previous => [...previous, line]); return line.rowKey;
      }}
      onRemove={key => { if (!locked) setLines(previous => previous.filter(line => line.rowKey !== key)); }} />
    <div className="flex flex-wrap gap-3">
      <Button type="button" disabled={locked} onClick={onClose}><L10n>Đóng</L10n></Button>
      <Button type="submit" loading={busy} variant="primary"><L10nValue>{uncertain ? "Gửi lại đúng lần sửa" : "Lưu đơn thuốc"}</L10nValue></Button>
    </div>
  </form>;
}
