"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";

import { useEffect, useState } from "react";
import { getSessionKey } from "@/lib/session";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
export interface PickerPage { items: { id: string; label: string }[]; totalPages: number }
export function RecordPicker({ id, label, value, onChange, load, disabled = false, searchable = true, required = false }: {
  id: string; label: string; value: string; onChange: (id: string) => void;
  load: (keyword: string, page: number) => Promise<PickerPage>; disabled?: boolean; searchable?: boolean; required?: boolean;
}) {
  const { localize } = useLocale();
  const [keyword, setKeyword] = useState("");
  const [request, setRequest] = useState({ keyword: "", page: 0, attempt: 0 });
  const [state, setState] = useState<{ page?: PickerPage; error?: string }>({});
  const [selection, setSelection] = useState<{ id: string; label: string } | null>(null);
  useEffect(() => {
    let disposed = false;
    const session = getSessionKey();
    const timer = window.setTimeout(() => {
      Promise.resolve().then(() => load(request.keyword, request.page)).then(page => {
        if (!disposed && getSessionKey() === session) setState({ page });
      }).catch(cause => {
        if (!disposed && getSessionKey() === session) setState({ error: cause instanceof Error ? cause.message : "Không thể tải lựa chọn." });
      });
    }, 200);
    return () => { disposed = true; window.clearTimeout(timer); };
  }, [load, request]);
  function go(page: number) { setState({}); setRequest(previous => ({ ...previous, page, attempt: previous.attempt + 1 })); }
  return <div className="min-w-0 space-y-2">
    <L10nValue>{searchable ? <Field label={localize(`Tìm ${label.toLowerCase()}`)} htmlFor={`${id}-search`}><input id={`${id}-search`} disabled={disabled} value={keyword} placeholder={localize("Nhập từ khóa để tìm")} className={controlClassName} onChange={e => { setKeyword(e.target.value); setState({}); setRequest({ keyword: e.target.value.trim(), page: 0, attempt: 0 }); }} /></Field> : null}</L10nValue>
    <Field label={localize(label)} htmlFor={id} required={required}><select id={id} value={value} required={required} disabled={disabled} className={controlClassName} onChange={e => { const item = state.page?.items.find(item => item.id === e.target.value); setSelection(item ?? null); onChange(e.target.value); }}>
      <option value=""><L10n>{"Chưa chọn"}</L10n></option>
      <L10nValue>{value && !state.page?.items.some(item => item.id === value) ? <option value={value}><L10nValue>{selection?.id === value ? selection.label : "Hồ sơ đang được chọn"}</L10nValue></option> : null}</L10nValue>
      <L10nValue>{state.page?.items.map(item => <option key={item.id} value={item.id}><L10nValue>{item.label}</L10nValue></option>)}</L10nValue>
    </select></Field>
    <L10nValue>{state.error ? <div role="alert" className="text-sm text-danger"><L10nValue>{state.error}</L10nValue> <Button type="button" disabled={disabled} onClick={() => go(request.page)}><L10n>{"Thử lại"}</L10n></Button></div> : !state.page ? <p role="status" className="text-xs text-muted-foreground"><L10n>{"Đang tải lựa chọn…"}</L10n></p> : state.page.items.length === 0 ? <p className="text-xs text-muted-foreground"><L10n>{"Không có kết quả ở trang này."}</L10n></p> : null}</L10nValue>
    <L10nValue>{state.page && state.page.totalPages > 1 ? <div className="flex items-center gap-2 text-xs"><Button type="button" disabled={disabled || request.page === 0} onClick={() => go(request.page - 1)}><L10n>{"Trang trước"}</L10n></Button><span><L10n>{"Trang"}</L10n> <L10nValue>{request.page + 1}</L10nValue>/{state.page.totalPages}</span><Button type="button" disabled={disabled || request.page + 1 >= state.page.totalPages} onClick={() => go(request.page + 1)}><L10n>{"Trang sau"}</L10n></Button></div> : null}</L10nValue>
  </div>;
}
