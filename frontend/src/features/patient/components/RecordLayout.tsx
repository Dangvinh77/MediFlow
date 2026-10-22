import Link from "next/link";
import type { ReactNode } from "react";

export function FormSection({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return <section className="rounded-xl border border-border bg-surface">
    <div className="border-b border-border px-5 py-4 sm:px-6"><h2 className="font-semibold">{title}</h2><p className="mt-1 text-sm text-muted-foreground">{description}</p></div>
    <div className="grid gap-5 p-5 sm:p-6 md:grid-cols-2">{children}</div>
  </section>;
}

export function DetailSection({ title, children }: { title: string; children: ReactNode }) {
  return <section className="rounded-xl border border-border bg-surface"><h3 className="border-b border-border px-5 py-4 font-semibold sm:px-6">{title}</h3><dl className="grid gap-6 p-5 sm:grid-cols-2 sm:p-6">{children}</dl></section>;
}

export function RecordIdentity({ name, subtitle, children }: { name: string; subtitle: string; children?: ReactNode }) {
  const initials = name.trim().split(/\s+/).slice(-2).map((part) => part[0]).join("").toUpperCase();
  return <div className="flex flex-wrap items-center gap-4 rounded-xl border border-border bg-surface p-5 sm:p-6">
    <span aria-hidden="true" className="flex size-14 shrink-0 items-center justify-center rounded-full bg-primary/10 text-lg font-semibold text-primary">{initials}</span>
    <div className="min-w-0 flex-1"><h2 className="break-words text-xl font-semibold">{name}</h2><p className="mt-1 text-sm text-muted-foreground">{subtitle}</p></div>{children}
  </div>;
}

export function RecordBreadcrumb({ href, parent, current }: { href: string; parent: string; current: string }) {
  return <nav aria-label="Đường dẫn" className="mt-5 flex flex-wrap items-center gap-2 text-sm"><Link href={href} className="rounded text-muted-foreground hover:text-primary focus-visible:outline-2 focus-visible:outline-primary">{parent}</Link><span aria-hidden="true" className="text-muted-foreground">/</span><span aria-current="page">{current}</span></nav>;
}
