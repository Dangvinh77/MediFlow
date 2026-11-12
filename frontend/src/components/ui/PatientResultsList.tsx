"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import type { PageResult } from "@/lib/types";
import { AsyncState } from "./AsyncState";
import { Button } from "./Button";

interface Props<T> {
  load: (page: number) => Promise<PageResult<T>>;
  render: (item: T) => ReactNode;
  empty: string;
}
export function PatientResultsList<T>(props: Props<T>) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <ResultsPage key={session} {...props} />;
}
function ResultsPage<T>({ load, render, empty }: Props<T>) {
  const { localize } = useLocale();
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState<{ data?: PageResult<T>; error?: string }>({});
  useEffect(() => {
    let active = true;
    load(page).then(data => { if (active) setState({ data }); })
      .catch((cause: unknown) => { if (active) setState({ error: cause instanceof Error ? cause.message : "Không thể tải kết quả." }); });
    return () => { active = false; };
  }, [load, page, retry]);
  function changePage(next: number) { setState({}); setPage(next); }
  if (state.error) return <div className="space-y-3"><p role="alert"><L10nValue>{state.error}</L10nValue></p><Button onClick={() => { setState({}); setRetry(value => value + 1); }}><L10n>{"Thử lại"}</L10n></Button></div>;
  if (!state.data) return <AsyncState kind="loading" message={localize("Đang tải kết quả…")} />;
  return <div className="space-y-5">
    <L10nValue>{state.data.content.length ? state.data.content.map(render) : <p role="status"><L10nValue>{empty}</L10nValue></p>}</L10nValue>
    <nav aria-label={localize("Phân trang kết quả")} className="flex items-center gap-4">
      <Button variant="secondary" disabled={page === 0} onClick={() => changePage(page - 1)}><L10n>{"Trang trước"}</L10n></Button>
      <span>Trang <L10nValue>{page + 1}</L10nValue> / <L10nValue>{Math.max(1, state.data.totalPages)}</L10nValue></span>
      <Button variant="secondary" disabled={page + 1 >= state.data.totalPages} onClick={() => changePage(page + 1)}>Trang sau</Button>
    </nav>
  </div>;
}
