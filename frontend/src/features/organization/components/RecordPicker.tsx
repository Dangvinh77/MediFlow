"use client";
import { useEffect, useState } from "react";
import { getSessionKey } from "@/lib/session";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
export interface PickerPage { items: { id: string; label: string }[]; totalPages: number }
export function RecordPicker({ id, label, value, onChange, load, disabled = false, searchable = true, required = false }: {
  id: string; label: string; value: string; onChange: (id: string) => void;
  load: (keyword: string, page: number) => Promise<PickerPage>; disabled?: boolean; searchable?: boolean; required?: boolean;
}) {
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
    {searchable ? <Field label={`Tìm ${label.toLowerCase()}`} htmlFor={`${id}-search`}><input id={`${id}-search`} disabled={disabled} value={keyword} placeholder="Nhập từ khóa để tìm" className={controlClassName} onChange={e => { setKeyword(e.target.value); setState({}); setRequest({ keyword: e.target.value.trim(), page: 0, attempt: 0 }); }} /></Field> : null}
    <Field label={label} htmlFor={id} required={required}><select id={id} value={value} required={required} disabled={disabled} className={controlClassName} onChange={e => { const item = state.page?.items.find(item => item.id === e.target.value); setSelection(item ?? null); onChange(e.target.value); }}>
      <option value="">Chưa chọn</option>
      {value && !state.page?.items.some(item => item.id === value) ? <option value={value}>{selection?.id === value ? selection.label : "Hồ sơ đang được chọn"}</option> : null}
      {state.page?.items.map(item => <option key={item.id} value={item.id}>{item.label}</option>)}
    </select></Field>
    {state.error ? <div role="alert" className="text-sm text-danger">{state.error} <Button type="button" disabled={disabled} onClick={() => go(request.page)}>Thử lại</Button></div> : !state.page ? <p role="status" className="text-xs text-muted-foreground">Đang tải lựa chọn…</p> : state.page.items.length === 0 ? <p className="text-xs text-muted-foreground">Không có kết quả ở trang này.</p> : null}
    {state.page && state.page.totalPages > 1 ? <div className="flex items-center gap-2 text-xs"><Button type="button" disabled={disabled || request.page === 0} onClick={() => go(request.page - 1)}>Trang trước</Button><span>Trang {request.page + 1}/{state.page.totalPages}</span><Button type="button" disabled={disabled || request.page + 1 >= state.page.totalPages} onClick={() => go(request.page + 1)}>Trang sau</Button></div> : null}
  </div>;
}
