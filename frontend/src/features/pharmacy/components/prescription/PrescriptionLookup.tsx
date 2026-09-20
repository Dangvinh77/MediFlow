"use client";

import { useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import type { Role } from "@/lib/roles";
import { getRole } from "@/lib/session";
import { getPharmacyCapabilities } from "../../permissions";
import { isUuid } from "../../utils";

function subscribeToRoleChanges(onStoreChange: () => void) {
  window.addEventListener("storage", onStoreChange);
  window.addEventListener("focus", onStoreChange);

  return () => {
    window.removeEventListener("storage", onStoreChange);
    window.removeEventListener("focus", onStoreChange);
  };
}

function getServerRole(): Role | null {
  return null;
}

export function PrescriptionLookup() {
  const router = useRouter();
  const role = useSyncExternalStore(
    subscribeToRoleChanges,
    getRole,
    getServerRole,
  );
  const [prescriptionId, setPrescriptionId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const capabilities = getPharmacyCapabilities(role);

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const id = prescriptionId.trim();
    if (!isUuid(id)) {
      setError("Mã đơn thuốc phải là UUID hợp lệ.");
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
    <section className="mt-6 max-w-2xl rounded-xl border border-border bg-surface p-6">
      <h2 className="text-base font-semibold">Nhập mã đơn thuốc</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Tra cứu cần mã UUID đã biết; hệ thống không gọi API liệt kê toàn bộ đơn thuốc.
      </p>
      <form onSubmit={submit} noValidate className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="prescription-id" className="mb-1 block text-sm font-medium">Mã đơn thuốc</label>
          <input
            id="prescription-id"
            value={prescriptionId}
            onChange={(event) => {
              setPrescriptionId(event.target.value);
              setError(null);
            }}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? "prescription-id-error" : undefined}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            className={`w-full rounded-lg border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground ${error ? "border-danger" : "border-border"}`}
          />
          {error && <p id="prescription-id-error" className="mt-1 text-xs text-danger">{error}</p>}
        </div>
        <button type="submit" className="rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90">
          Mở chi tiết
        </button>
      </form>
    </section>
  );
}
