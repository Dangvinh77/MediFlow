"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { Field, controlClassName } from "@/components/ui/Field";
import { StatusBadge, type StatusTone } from "@/components/ui/StatusBadge";
import { prepareCommandIntent } from "@/lib/command-intent";
import { getSessionKey } from "@/lib/session";
import { useCommand } from "@/lib/use-command";
import { medicalRecordApi } from "../api";
import type { RecordLabOrderDTO } from "../types";

const statusPresentation: Record<string, { label: string; tone: StatusTone }> = {
  NOT_RECEIVED: { label: "Lab chưa tiếp nhận", tone: "warning" },
  PENDING: { label: "Chờ xử lý", tone: "warning" },
  AWAITING_PAYMENT: { label: "Chờ thanh toán", tone: "warning" },
  READY: { label: "Sẵn sàng thực hiện", tone: "info" },
  IN_PROGRESS: { label: "Đang thực hiện", tone: "info" },
  COMPLETED: { label: "Đã có kết quả", tone: "success" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
};

const POSTPONE_PREFIX = "Hoãn sang lần khám sau: ";
type LoadState = { kind: "loading" } | { kind: "unavailable" } | { kind: "ready"; orders: RecordLabOrderDTO[] };
/** Cancel and postpone go to Lab by test id; withdraw takes back an order Lab never received. */
type Action = { orderId: string; testId: string | null; mode: "cancel" | "postpone" | "withdraw" };
const reasonLabels = { cancel: "Lý do hủy", postpone: "Lý do hoãn", withdraw: "Lý do rút chỉ định" } as const;

interface RecordLabOrdersPanelProps {
  recordId: string;
  /** Supplied by the route only while the visit can still change its orders. */
  cancelOrder?: (testId: string, reason: string) => Promise<unknown>;
  /** True only while the examining doctor can still change this visit's orders. */
  canWithdraw?: boolean;
}

export function RecordLabOrdersPanel({ recordId, cancelOrder, canWithdraw = false }: RecordLabOrdersPanelProps) {
  const { localize } = useLocale();
  const command = useCommand();
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [action, setAction] = useState<Action | null>(null);
  const [reason, setReason] = useState("");

  const load = useCallback(() => {
    let current = true;
    medicalRecordApi.labOrders(recordId)
      .then((orders) => { if (current) setState({ kind: "ready", orders }); })
      .catch(() => { if (current) setState({ kind: "unavailable" }); });
    return () => { current = false; };
  }, [recordId]);

  useEffect(() => load(), [load]);

  function begin(next: Action) {
    setAction(next);
    setReason("");
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = reason.trim();
    if (!action || !text) return;
    const done = () => {
      setAction(null);
      setReason("");
      load();
    };
    if (action.mode === "withdraw") {
      const body = { reason: text };
      void command.run(async () => {
        const intent = await prepareCommandIntent(`withdraw-lab-order:${recordId}:${action.orderId}`, getSessionKey(), body);
        try {
          const withdrawn = await medicalRecordApi.withdrawLabOrder(recordId, action.orderId, body,
            { idempotencyKey: intent.key, correlationId: intent.correlationId });
          intent.settle();
          return withdrawn;
        } catch (cause) {
          intent.settle(cause);
          throw cause;
        }
      }, done);
      return;
    }
    if (!cancelOrder || !action.testId) return;
    const sent = action.mode === "postpone" ? `${POSTPONE_PREFIX}${text}` : text;
    void command.run(() => cancelOrder(action.testId as string, sent), done);
  }

  if (state.kind === "loading") return <p role="status" className="mt-3 text-sm text-muted-foreground"><L10n>{"Đang kiểm tra trạng thái xét nghiệm…"}</L10n></p>;
  if (state.kind === "unavailable") {
    return <div role="alert" className="mt-3 border-l-2 border-warning px-4 py-2 text-sm">
      <p><L10n>{"Không kiểm tra được trạng thái xét nghiệm lúc này. Danh sách chỉ định có thể chưa đầy đủ."}</L10n></p>
      <Button type="button" variant="secondary" className="mt-2" onClick={() => { setState({ kind: "loading" }); load(); }}><L10n>{"Thử lại"}</L10n></Button>
    </div>;
  }
  if (!state.orders.length) return <p className="mt-3 text-sm text-muted-foreground"><L10n>{"Chưa có chỉ định xét nghiệm."}</L10n></p>;

  return <div className="mt-3 space-y-2">
    <CommandFeedback error={command.error} uncertain={command.uncertain} />
    <ul className="divide-y divide-border">
      <L10nValue>{state.orders.map((order) => {
        const presentation = statusPresentation[order.status] ?? { label: order.status, tone: "neutral" as StatusTone };
        const open = order.testId !== null && order.status !== "COMPLETED" && order.status !== "CANCELLED";
        const unreceived = order.testId === null && order.status === "NOT_RECEIVED";
        const editing = action !== null && action.orderId === order.sourceOrderId;
        return <li key={order.sourceOrderId} className="py-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-sm font-medium">{order.labType}</span>
            <StatusBadge tone={presentation.tone}><L10nValue>{presentation.label}</L10nValue></StatusBadge>
          </div>
          <L10nValue>{open && cancelOrder && !editing ? <div className="mt-1 flex flex-wrap gap-2">
            <Button type="button" variant="secondary" disabled={command.busy || command.uncertain} onClick={() => begin({ orderId: order.sourceOrderId, testId: order.testId, mode: "cancel" })}><L10n>{"Hủy chỉ định"}</L10n></Button>
            <Button type="button" variant="secondary" disabled={command.busy || command.uncertain} onClick={() => begin({ orderId: order.sourceOrderId, testId: order.testId, mode: "postpone" })}><L10n>{"Hoãn sang lần sau"}</L10n></Button>
          </div> : null}</L10nValue>
          <L10nValue>{unreceived && canWithdraw && !editing ? <div className="mt-1 flex flex-wrap items-center gap-2">
            <Button type="button" variant="secondary" disabled={command.busy || command.uncertain} onClick={() => begin({ orderId: order.sourceOrderId, testId: null, mode: "withdraw" })}><L10n>{"Rút chỉ định"}</L10n></Button>
            <span className="text-xs text-muted-foreground"><L10n>{"Lab chưa nhận chỉ định này nên có thể rút lại."}</L10n></span>
          </div> : null}</L10nValue>
          <L10nValue>{editing ? <form onSubmit={submit} className="mt-2 space-y-2">
            <Field label={localize(reasonLabels[action.mode])} htmlFor={`lab-order-reason-${order.sourceOrderId}`} required>
              <textarea id={`lab-order-reason-${order.sourceOrderId}`} rows={2} maxLength={400} className={controlClassName} value={reason} onChange={(event) => setReason(event.target.value)} />
            </Field>
            <div className="flex flex-wrap gap-2">
              <Button type="submit" variant="primary" loading={command.busy} disabled={!reason.trim() || command.uncertain}><L10n>{"Xác nhận"}</L10n></Button>
              <Button type="button" variant="secondary" disabled={command.busy} onClick={() => setAction(null)}><L10n>{"Bỏ qua"}</L10n></Button>
            </div>
          </form> : null}</L10nValue>
        </li>;
      })}</L10nValue>
    </ul>
  </div>;
}
