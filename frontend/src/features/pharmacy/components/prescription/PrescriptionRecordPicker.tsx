"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState } from "react";
import { ReferenceInput, type ReferenceInputProps } from "@/components/references/ReferenceInput";
import { useReferenceDirectory, type ReferencePage } from "@/components/references/ReferenceProvider";
import { Button } from "@/components/ui/Button";

type Props = Omit<ReferenceInputProps, "kind" | "scope"> & { patientId: string };

export function PrescriptionRecordPicker({ patientId, ...props }: Props) {
  const directory = useReferenceDirectory();
  if (!directory?.canSearch("record")) {
    return <ReferenceInput {...props} kind="record" scope={patientId} disabled={props.disabled || !patientId} />;
  }
  return <PatientRecords key={patientId} {...props} patientId={patientId} directory={directory} />;
}

function PatientRecords({ patientId, directory, onValueChange, ...props }: Props & {
  directory: NonNullable<ReturnType<typeof useReferenceDirectory>>;
}) {
  const { localize } = useLocale();
  const [result, setResult] = useState<ReferencePage | null>(null);
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(Boolean(patientId));
  const change = useRef(onValueChange);
  useEffect(() => { change.current = onValueChange; }, [onValueChange]);

  useEffect(() => {
    if (!patientId || props.disabled) return;
    let current = true;
    directory.search("record", "", page, patientId).then(next => {
      if (!current) return;
      setResult(previous => ({ ...next, items: page === 0 ? next.items : [
        ...(previous?.items ?? []), ...next.items.filter(item => !previous?.items.some(old => old.id === item.id)),
      ] }));
      // A single item on an incomplete page is not a single patient record.
      if (page === 0 && next.items.length === 1 && !next.hasMore) change.current(next.items[0].id);
    }).catch((cause: unknown) => {
      if (current) setError(cause instanceof Error ? cause.message : "Không thể tải hồ sơ khám. Hãy thử lại.");
    }).finally(() => { if (current) setBusy(false); });
    return () => { current = false; };
  }, [directory, patientId, page, retry, props.disabled]);

  const single = result?.items.length === 1 && !result.hasMore;
  const selected = result?.items.find(item => item.id === props.value);
  const hint = !patientId ? "Chọn bệnh nhân để xem hồ sơ khám."
    : busy && !result ? "Đang tải hồ sơ khám…"
    : !result?.items.length ? "Bệnh nhân chưa có hồ sơ khám."
    : single ? "Đã tự chọn hồ sơ khám duy nhất của bệnh nhân."
    : "Chọn một hồ sơ khám của bệnh nhân này.";

  return <div className="space-y-2" aria-busy={busy || undefined}>
    <L10nValue>{single ? <input {...props} value={result.items[0].label} title={localize(result.items[0].label)} readOnly />
      : <select id={props.id} className={props.className} value={props.value} onChange={event => onValueChange(event.target.value)}
        disabled={props.disabled || busy || !result?.items.length} aria-required={props["aria-required"]}
        aria-invalid={props["aria-invalid"]} aria-describedby={props["aria-describedby"]}>
        <option value=""><L10nValue>{error && !result ? "Chưa tải được hồ sơ khám" : !result?.items.length ? hint : "Chọn hồ sơ khám"}</L10nValue></option>
        <L10nValue>{result?.items.map(item => <option key={item.id} value={item.id}><L10nValue>{item.label}</L10nValue></option>)}</L10nValue>
      </select>}</L10nValue>
    <L10nValue>{selected && <p aria-label={localize("Nội dung hồ sơ đã chọn")} className="break-words text-sm text-foreground"><L10nValue>{selected.label}</L10nValue></p>}</L10nValue>
    <L10nValue>{error ? <div role="alert" className="space-y-2 text-sm text-danger">
      <p><L10nValue>{error}</L10nValue></p>
      <Button type="button" variant="secondary" disabled={props.disabled || busy} onClick={() => {
        setError(null); setBusy(true); setRetry(value => value + 1);
      }}><L10n>{"Thử tải lại danh sách hồ sơ"}</L10n></Button>
    </div> : <p role="status" className="text-sm text-muted-foreground"><L10nValue>{hint}</L10nValue></p>}</L10nValue>
    <L10nValue>{result?.hasMore && !error && <Button type="button" variant="secondary" disabled={props.disabled || busy} onClick={() => {
      setBusy(true); setPage(value => value + 1);
    }}><L10nValue>{busy ? "Đang tải thêm…" : "Xem thêm hồ sơ"}</L10nValue></Button>}</L10nValue>
  </div>;
}
