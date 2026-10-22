"use client";
import { useEffect, useRef, useState, type InputHTMLAttributes, type Ref } from "react";
import { useReferenceDirectory, type ReferencePage } from "./ReferenceProvider";
export interface ReferenceInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> {
  kind: string; scope?: string; value: string; onValueChange: (value: string) => void;
  ref?: Ref<HTMLInputElement>;
}
export function ReferenceInput(props: ReferenceInputProps) {
  const directory = useReferenceDirectory();
  return <Picker key={`${props.kind}:${props.scope ?? ""}`} {...props} directory={directory} />;
}

function Picker({ kind, scope, onValueChange, value, directory, ...props }: ReferenceInputProps & { directory: ReturnType<typeof useReferenceDirectory> }) {
  const [query, setQuery] = useState("");
  const [chosen, setChosen] = useState<{ id: string; label: string } | null>(null);
  const [result, setResult] = useState<ReferencePage | null>(null);
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);
  useEffect(() => () => { generation.current++; }, []);
  // Resolve an externally supplied selection without putting its UUID in the search box.
  useEffect(() => {
    let active = true;
    if (value && directory && directory.canSearch(kind) && chosen?.id !== value) {
      directory.resolve(kind, value).then(label => {
        if (active && label) setChosen({ id: value, label });
      }).catch(() => {});
    }
    return () => { active = false; };
  }, [value, directory, kind, chosen?.id]);

  if (!directory) return <input {...props} value={value} onChange={event => onValueChange(event.target.value)} />;

  async function search(nextPage = 0) {
    if (!directory || !directory.canSearch(kind) || props.disabled) return;
    const current = ++generation.current;
    setBusy(true); setError(null); setResult(null);
    try {
      const next = await directory.search(kind, query.trim(), nextPage, scope);
      if (generation.current === current) { setResult(next); setPage(nextPage); }
    } catch (cause) {
      if (generation.current === current) setError(cause instanceof Error ? cause.message : "Không thể tải lựa chọn. Hãy thử lại.");
    } finally { if (generation.current === current) setBusy(false); }
  }

  if (!directory.canSearch(kind)) return <div className="space-y-2">
    <p className="text-sm text-muted-foreground">Chưa có quyền tìm kiếm thông tin này. Dùng mã tham chiếu đã được cung cấp.</p>
    <details><summary className="cursor-pointer text-sm text-primary">Nhập mã tham chiếu</summary><input {...props} value={value} onChange={event => onValueChange(event.target.value)} /></details>
  </div>;

  return <div className="space-y-2">
    <div className="flex gap-2">
      <input {...props} value={chosen?.id === value ? chosen.label : query} placeholder="Nhập tên hoặc thông tin để tìm" autoComplete="off" onKeyDown={event => { if (event.key === "Enter") { event.preventDefault(); void search(); } }} onChange={event => {
        generation.current++; setBusy(false); setChosen(null); setQuery(event.target.value); setResult(null); setError(null); onValueChange("");
      }} />
      <button type="button" aria-label="Tìm lựa chọn" disabled={props.disabled || busy} onClick={() => void search()} className="min-h-11 shrink-0 rounded-lg border border-border px-3 text-sm text-primary disabled:opacity-50">{busy ? "Đang tìm…" : "Tìm"}</button>
    </div>
    {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
    {result ? <div aria-live="polite" className="rounded-lg border border-border p-2">
      {result.hint ? <p className="mb-2 text-xs text-muted-foreground">{result.hint}</p> : null}
      {!result.items.length ? <p className="text-sm text-muted-foreground">Không có kết quả phù hợp.</p> : <ul className="max-h-60 overflow-y-auto">{result.items.map(option => <li key={option.id}><button type="button" disabled={props.disabled} onClick={() => {
        generation.current++; setChosen(option); setQuery(""); setResult(null); onValueChange(option.id);
      }} className="min-h-11 w-full rounded px-3 py-2 text-left text-sm hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary">{option.label}</button></li>)}</ul>}
      <div className="flex gap-3">{page > 0 ? <button type="button" disabled={busy || props.disabled} onClick={() => void search(page - 1)} className="min-h-11 text-sm text-primary">Trang trước</button> : null}{result.hasMore ? <button type="button" disabled={busy || props.disabled} onClick={() => void search(page + 1)} className="min-h-11 text-sm text-primary">Trang sau</button> : null}</div>
    </div> : null}
  </div>;
}
