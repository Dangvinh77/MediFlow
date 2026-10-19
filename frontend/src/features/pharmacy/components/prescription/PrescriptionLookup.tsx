"use client";

import { useId, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import type { Role } from "@/lib/roles";
import { getPharmacyCapabilities } from "../../permissions";
import { isUuid } from "../../utils";
import { PharmacySessionBoundary } from "../PharmacySessionBoundary";

function getServerRole(): Role | null {
  return null;
}

export function PrescriptionLookup({ variant = "standalone" }: { variant?: "standalone" | "compact" }) {
  return <PharmacySessionBoundary><PrescriptionLookupContent variant={variant} /></PharmacySessionBoundary>;
}

function PrescriptionLookupContent({ variant }: { variant: "standalone" | "compact" }) {
  const router = useRouter();
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const role = useSyncExternalStore(
    subscribeToAuthChanges,
    getRole,
    getServerRole,
  );
  const [prescriptionId, setPrescriptionId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const capabilities = getPharmacyCapabilities(role);

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!getPharmacyCapabilities(getRole()).canReadPrescription) return;
    const id = prescriptionId.trim();
    if (!isUuid(id)) {
      setError("Mã đơn thuốc phải là UUID hợp lệ.");
      inputRef.current?.focus();
      return;
    }
    setError(null);
    router.push(`/pharmacy/prescriptions/${id}`);
  }

  if (role === null) {
    return <p className="mt-6 text-sm text-muted-foreground">Đang kiểm tra quyền…</p>;
  }

  if (!capabilities.canReadPrescription) {
    return (
      <p role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
        Tài khoản của bạn không có quyền tra cứu đơn thuốc.
      </p>
    );
  }

  return (
    <section className={`mt-6 rounded-xl border border-border bg-surface p-4 sm:p-5 ${variant === "compact" ? "grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)] xl:items-center" : "max-w-2xl"}`}>
      <div>
        <h2 className="text-base font-semibold">Tra cứu đơn thuốc</h2>
        <p className="mt-1 text-sm text-muted-foreground">Nhập mã để mở chi tiết đơn thuốc.</p>
      </div>
      <form onSubmit={submit} noValidate className={`flex flex-col gap-3 sm:flex-row sm:items-end ${variant === "standalone" ? "mt-5" : ""}`}>
        <div className="min-w-0 flex-1">
          <Field label="Mã đơn thuốc" htmlFor={inputId} error={error ?? undefined}>
          <input
            ref={inputRef}
            id={inputId}
            value={prescriptionId}
            onChange={(event) => {
              setPrescriptionId(event.target.value);
              setError(null);
            }}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${inputId}-error` : undefined}
            placeholder="Mã đơn thuốc (UUID)"
            className={`${controlClassName} ${error ? "border-danger" : ""}`}
          />
          </Field>
        </div>
        <Button type="submit" variant="primary">Tra cứu</Button>
      </form>
    </section>
  );
}
