"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { isUuid } from "@/lib/validation";

export function PrescriptionAdjustmentLookupForm() {
  const router = useRouter();
  const [prescriptionId, setPrescriptionId] = useState("");
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const id = prescriptionId.trim();
    if (!isUuid(id)) {
      setError("Mã đơn thuốc phải là UUID hợp lệ.");
      return;
    }
    setError(null);
    router.push(`/billing/prescription-adjustments/${id}`);
  }

  return (
    <form onSubmit={submit} noValidate className="mt-6 max-w-xl space-y-5 rounded-xl border border-border bg-surface p-6">
      <p className="text-sm text-muted-foreground">
        Nhập mã đơn thuốc (prescription id) đã bị từ chối/huỷ sau khi đã thu tiền, để xem các khoản
        hoàn tiền còn phải trả cho bệnh nhân và ghi nhận hoàn tiền.
      </p>
      {error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}
      <Field label="Mã đơn thuốc" htmlFor="prescription-adjustment-id" required>
        <input
          id="prescription-adjustment-id"
          required
          value={prescriptionId}
          onChange={(event) => setPrescriptionId(event.target.value)}
          placeholder="Nhập UUID đơn thuốc"
          className={controlClassName}
        />
      </Field>
      <Button type="submit" variant="primary">Xem khoản hoàn tiền</Button>
    </form>
  );
}
