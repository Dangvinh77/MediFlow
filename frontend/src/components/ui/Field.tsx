
import { L10nValue } from "@/components/i18n/LocaleProvider";
import type { ReactNode } from "react";

interface FieldProps {
  label: string;
  htmlFor: string;
  hint?: string;
  error?: string;
  required?: boolean;
  children: ReactNode;
}

export function Field({ label, htmlFor, hint, error, required = false, children }: FieldProps) {
  const messageId = `${htmlFor}-${error ? "error" : "hint"}`;
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-sm font-medium text-foreground">
        <L10nValue>{label}</L10nValue>
        <L10nValue>{required ? <><span aria-hidden="true" className="ml-1 text-danger">*</span><span className="sr-only"><L10nValue>{"bắt buộc"}</L10nValue></span></> : null}</L10nValue>
      </label>
      <L10nValue>{children}</L10nValue>
      <L10nValue>{error ? (
        <p id={messageId} role="alert" className="text-sm text-danger"><L10nValue>{error}</L10nValue></p>
      ) : hint ? (
        <p id={messageId} className="text-sm text-muted-foreground"><L10nValue>{hint}</L10nValue></p>
      ) : null}</L10nValue>
    </div>
  );
}

export const controlClassName = "min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:bg-surface-muted";
