"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { isUuid } from "@/lib/validation";
import { billingApi } from "../api";
import type { OutboxReplayResult } from "../types";

interface RequestError {
  message: string;
  correlationId: string | null;
}

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể phát lại event.",
    correlationId: null,
  };
}

export function OutboxReplayForm() {
  const [eventId, setEventId] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);
  const [error, setError] = useState<RequestError | null>(null);
  const [result, setResult] = useState<OutboxReplayResult | null>(null);
  const [replaying, setReplaying] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const id = eventId.trim();
    if (!isUuid(id)) {
      setValidationError("Mã event phải là UUID hợp lệ.");
      return;
    }
    setValidationError(null);
    setError(null);
    setResult(null);
    setReplaying(true);
    try {
      const outcome = await billingApi.replayOutboxEvent(id);
      setResult(outcome);
    } catch (cause: unknown) {
      setError(getRequestError(cause));
    } finally {
      setReplaying(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="mt-6 max-w-xl space-y-5 rounded-xl border border-border bg-surface p-6">
      <p className="text-sm text-muted-foreground">
        Phát lại (replay) một outbox event của Billing đang bị giữ lại (quarantine/HELD). Chỉ dùng
        khi được vận hành viên xác nhận, không đoán event id.
      </p>

      {validationError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{validationError}</p> : null}

      {error ? (
        <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          {error.message}
          {error.correlationId ? (
            <span className="mt-1 block text-xs text-muted-foreground">Mã theo dõi: <span className="font-mono">{error.correlationId}</span></span>
          ) : null}
        </p>
      ) : null}

      {result ? (
        <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">
          Đã phát lại event <span className="font-mono">{result.eventId}</span>.
        </p>
      ) : null}

      <Field label="Mã event (outbox)" htmlFor="outbox-event-id" required>
        <input
          id="outbox-event-id"
          required
          value={eventId}
          onChange={(event) => setEventId(event.target.value)}
          placeholder="Nhập UUID event"
          className={controlClassName}
        />
      </Field>

      <Button type="submit" variant="primary" loading={replaying}>Phát lại event</Button>
    </form>
  );
}
