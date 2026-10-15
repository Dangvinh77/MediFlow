"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { isUuid } from "@/lib/validation";

export function PaymentRequestLookupForm() {
  const router = useRouter();
  const [requestId, setRequestId] = useState("");
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const id = requestId.trim();
    if (!isUuid(id)) {
      setError("Mã khoản phí phải là UUID hợp lệ.");
      return;
    }
    setError(null);
    router.push(`/billing/payment-requests/${id}`);
  }

  return (
    <form onSubmit={submit} noValidate className="mt-6 max-w-xl space-y-5 rounded-xl border border-border bg-surface p-6">
      <p className="text-sm text-muted-foreground">
        Nhập đúng mã khoản phí (payment request id) do màn hình Khám/Xét nghiệm cung cấp cho bệnh
        nhân. Billing không tự tra cứu danh sách khoản phí đang chờ — chưa có API tìm kiếm.
      </p>
      {error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}
      <Field label="Mã khoản phí" htmlFor="payment-request-id" required>
        <input
          id="payment-request-id"
          required
          value={requestId}
          onChange={(event) => setRequestId(event.target.value)}
          placeholder="Nhập UUID khoản phí"
          className={controlClassName}
        />
      </Field>
      <Button type="submit" variant="primary">Tiếp tục thanh toán</Button>
    </form>
  );
}
