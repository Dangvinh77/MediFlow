"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { useCommand } from "@/lib/use-command";
import { isUuid } from "@/lib/validation";
import { billingApi } from "../api";
import type { OutboxReplayResult } from "../types";

export function OutboxReplayForm() {
  const router = useRouter();
  const [eventId, setEventId] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);
  const command = useCommand();
  const [result, setResult] = useState<OutboxReplayResult | null>(null);
  const replaying = command.busy;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const id = eventId.trim();
    if (!isUuid(id)) {
      setValidationError("Mã event phải là UUID hợp lệ.");
      return;
    }
    setValidationError(null);
    setResult(null);
    void command.run(() => billingApi.replayOutboxEvent(id), setResult);
  }

  return (
    <form onSubmit={submit} noValidate className="mt-6 max-w-xl space-y-5 rounded-xl border border-border bg-surface p-6">
      <p className="text-sm text-muted-foreground">
        Phát lại (replay) một outbox event của Billing đang bị giữ lại (quarantine/HELD). Chỉ dùng
        khi được vận hành viên xác nhận, không đoán event id.
      </p>

      {validationError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{validationError}</p> : null}

      <CommandFeedback error={command.error} uncertain={command.uncertain} />

      {result ? (
        <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">
          {result.replayed ? "Đã đưa event vào hàng đợi phát lại; chưa xác nhận bên nhận xử lý" : "Event không được phát lại"} <span className="font-mono"><ReferenceText value={result.eventId} fallback="Sự kiện" /></span>.
        </p>
      ) : null}

      <Field label="Mã event (outbox)" htmlFor="outbox-event-id" required>
        <input
          id="outbox-event-id"
          required
          disabled={replaying || command.uncertain}
          value={eventId}
          onChange={(event) => setEventId(event.target.value)}
          placeholder="Nhập UUID event"
          className={controlClassName}
        />
      </Field>

      <div className="flex gap-2">
        <Button type="submit" variant="primary" loading={replaying} disabled={command.uncertain}>Phát lại event</Button>
        <Button type="button" onClick={() => router.back()}>Quay lại</Button>
      </div>
    </form>
  );
}
