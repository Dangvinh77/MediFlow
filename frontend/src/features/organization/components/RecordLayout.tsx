import Link from "next/link";
import type { ReactNode } from "react";

export function DirectorySection({ title, description, actions, children }: { title: string; description?: string; actions?: ReactNode; children: ReactNode }) {
  return <section className="mt-2 overflow-hidden rounded-xl border border-border bg-surface">
    <div className="flex flex-wrap items-center justify-between gap-4 px-5 py-5 lg:px-6">
      <div className="min-w-0"><h2 className="text-lg font-semibold tracking-tight">{title}</h2>{description ? <p className="mt-1 text-sm leading-6 text-muted-foreground">{description}</p> : null}</div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
    {children}
  </section>;
}

export function FormSection({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return <section className="rounded-xl border border-border bg-surface">
    <div className="border-b border-border px-5 py-5 sm:px-6"><h2 className="text-base font-semibold">{title}</h2><p className="mt-1 max-w-prose text-sm leading-6 text-muted-foreground">{description}</p></div>
    <div className="grid gap-x-8 gap-y-5 p-5 sm:p-6 md:grid-cols-2">{children}</div>
  </section>;
}

export function DetailSection({ title, children }: { title: string; children: ReactNode }) {
  return <section className="rounded-xl border border-border bg-surface"><h3 className="border-b border-border px-5 py-4 text-base font-semibold sm:px-6">{title}</h3><dl className="grid gap-x-8 gap-y-6 p-5 sm:grid-cols-2 sm:p-6">{children}</dl></section>;
}

export function RecordIdentity({ name, subtitle, children }: { name: string; subtitle: string; children?: ReactNode }) {
  const initials = name.trim().split(/\s+/).slice(-2).map((part) => part[0]).join("").toUpperCase();
  return <div className="flex flex-wrap items-center gap-4 rounded-xl border border-border bg-surface p-5 sm:p-6">
    <span aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-full bg-surface-muted text-base font-semibold text-foreground">{initials}</span>
    <div className="min-w-0 flex-1"><h2 className="break-words text-2xl font-semibold tracking-tight">{name}</h2><p className="mt-1 text-sm leading-6 text-muted-foreground">{subtitle}</p></div>
    {children ? <div className="flex flex-wrap items-center gap-3">{children}</div> : null}
  </div>;
}

export function RecordBreadcrumb({ href, parent, current }: { href: string; parent: string; current: string }) {
  return <nav aria-label="Đường dẫn" className="mt-5 flex flex-wrap items-center gap-2 text-sm"><Link href={href} className="rounded text-muted-foreground underline-offset-4 hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{parent}</Link><span aria-hidden="true" className="text-muted-foreground">/</span><span aria-current="page">{current}</span></nav>;
}
