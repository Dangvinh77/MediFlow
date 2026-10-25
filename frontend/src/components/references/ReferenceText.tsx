"use client";
import { useEffect, useId, useState } from "react";
import { useReferenceDirectory } from "./ReferenceProvider";

interface ReferenceTextProps { kind?: string; value: string | null | undefined; fallback?: string; technical?: boolean }

export function ReferenceText(props: ReferenceTextProps) {
  return <ReferenceTextContent key={`${props.kind}:${props.value}`} {...props} />;
}

function ReferenceTextContent({ kind, value, fallback = "Tham chiếu", technical = false }: ReferenceTextProps) {
  const directory = useReferenceDirectory();
  const [expanded, setExpanded] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const technicalId = useId();
  const [resolved, setResolved] = useState<{ value: string; label: string | null; directory: typeof directory } | null>(null);
  useEffect(() => {
    let active = true;
    if (directory && kind && value && directory.canSearch(kind)) directory.resolve(kind, value).then(label => {
      if (active) setResolved({ value, label, directory });
    }).catch(() => { if (active) setResolved({ value, label: null, directory }); });
    return () => { active = false; };
  }, [directory, kind, value, attempt]);
  if (!value) return <span>—</span>;
  const label = resolved?.value === value && resolved.directory === directory ? resolved.label ?? `${fallback} · Chưa tải được tên` : `${fallback}${kind && directory ? directory.canSearch(kind) ? " · Đang chờ thông tin tên" : " · Chưa có quyền xem tên" : ""}`;
  return <span className="inline-block max-w-full font-sans text-sm">
    <span>{label}</span>
    {resolved?.value === value && resolved.directory === directory && resolved.label === null ? <button type="button" onClick={() => { setResolved(null); setAttempt(current => current + 1); }} className="ml-2 min-h-8 text-xs text-primary underline underline-offset-2">Tải lại tên</button> : null}
    {technical ? <span className="mt-1 block text-xs text-muted-foreground"><button type="button" aria-expanded={expanded} aria-controls={technicalId} onClick={() => setExpanded(current => !current)} className="min-h-8 cursor-pointer text-left underline underline-offset-2">Thông tin kỹ thuật</button><span id={technicalId} hidden={!expanded} className="break-all font-mono">{value}</span></span> : <span hidden>{value}</span>}
  </span>;
}
