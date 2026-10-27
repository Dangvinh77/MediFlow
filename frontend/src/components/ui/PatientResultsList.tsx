"use client";

import { useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import type { PageResult } from "@/lib/types";
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
  if (state.error) return <div className="space-y-3"><p role="alert">{state.error}</p><Button onClick={() => { setState({}); setRetry(value => value + 1); }}>Thử lại</Button></div>;
  if (!state.data) return <p role="status">Đang tải kết quả…</p>;
  return <div className="space-y-5">
    {state.data.content.length ? state.data.content.map(render) : <p role="status">{empty}</p>}
    <nav aria-label="Phân trang kết quả" className="flex items-center gap-4">
      <Button variant="secondary" disabled={page === 0} onClick={() => changePage(page - 1)}>Trang trước</Button>
      <span>Trang {page + 1} / {Math.max(1, state.data.totalPages)}</span>
      <Button variant="secondary" disabled={page + 1 >= state.data.totalPages} onClick={() => changePage(page + 1)}>Trang sau</Button>
    </nav>
  </div>;
}
