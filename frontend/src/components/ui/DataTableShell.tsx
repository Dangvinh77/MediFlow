
import { L10nValue } from "@/components/i18n/LocaleProvider";
import type { ReactNode } from "react";

interface DataTableShellProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
}

export function DataTableShell({ title, description, actions, children }: DataTableShellProps) {
  return (
    <section className="mt-6 overflow-hidden rounded-xl border border-border bg-surface">
      <div className="flex flex-col gap-3 px-4 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-5">
        <div>
          <h2 className="text-base font-semibold"><L10nValue>{title}</L10nValue></h2>
          <L10nValue>{description ? <p className="mt-0.5 text-sm text-muted-foreground"><L10nValue>{description}</L10nValue></p> : null}</L10nValue>
        </div>
        <L10nValue>{actions ? <div className="flex flex-wrap items-center gap-2"><L10nValue>{actions}</L10nValue></div> : null}</L10nValue>
      </div>
      <div className="overflow-x-auto"><L10nValue>{children}</L10nValue></div>
    </section>
  );
}
