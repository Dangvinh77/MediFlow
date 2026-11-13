"use client";

import { useRef, type KeyboardEvent, type ReactNode } from "react";
import { L10nValue } from "@/components/i18n/LocaleProvider";

export interface TabItem {
  id: string;
  label: string;
  /** Short count or state next to the label, e.g. "2" diagnoses. */
  badge?: ReactNode;
}

/**
 * WAI-ARIA tablist with automatic activation: arrow keys, Home and End move between tabs.
 * Panels stay mounted (see TabPanel), so a half-written form survives switching tabs.
 */
export function Tabs({ idPrefix, label, tabs, active, onChange }: { idPrefix: string; label: string; tabs: TabItem[]; active: string; onChange: (id: string) => void }) {
  const refs = useRef(new Map<string, HTMLButtonElement>());
  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const next = event.key === "ArrowRight" ? index + 1 : event.key === "ArrowLeft" ? index - 1 : event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 : null;
    if (next === null) return;
    event.preventDefault();
    const tab = tabs[(next + tabs.length) % tabs.length];
    onChange(tab.id);
    refs.current.get(tab.id)?.focus();
  }
  return <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto overflow-y-hidden border-b border-border [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
    <L10nValue>{tabs.map((tab, index) => {
      const selected = tab.id === active;
      return <button key={tab.id} ref={node => { if (node) refs.current.set(tab.id, node); else refs.current.delete(tab.id); }}
        type="button" role="tab" id={`${idPrefix}-tab-${tab.id}`} aria-controls={`${idPrefix}-panel-${tab.id}`} aria-selected={selected} tabIndex={selected ? 0 : -1}
        onClick={() => onChange(tab.id)} onKeyDown={event => onKeyDown(event, index)}
        className={`-mb-px inline-flex min-h-11 shrink-0 items-center gap-2 whitespace-nowrap border-b-2 px-4 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${selected ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:border-border hover:text-foreground"}`}>
        <L10nValue>{tab.label}</L10nValue>
        <L10nValue>{tab.badge !== undefined && tab.badge !== null ? <span className={`rounded-full px-2 py-0.5 text-xs tabular-nums ${selected ? "bg-primary/15" : "bg-surface-muted"}`}>{tab.badge}</span> : null}</L10nValue>
      </button>;
    })}</L10nValue>
  </div>;
}

export function TabPanel({ idPrefix, id, active, children, className = "" }: { idPrefix: string; id: string; active: string; children: ReactNode; className?: string }) {
  return <div role="tabpanel" id={`${idPrefix}-panel-${id}`} aria-labelledby={`${idPrefix}-tab-${id}`} hidden={id !== active} tabIndex={0} className={className}>
    {children}
  </div>;
}
