
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import { MediFlowLoader } from "@mediflow/loader";

export type AsyncStateProps =
  | { kind: "loading"; message: string }
  | { kind: "idle" | "empty"; message: string }
  | {
      kind: "error";
      message: string;
      correlationId?: string | null;
      onRetry: () => void;
    };

export function AsyncState(props: AsyncStateProps) {
  if (props.kind === "loading") {
    return (
      <div
        className="flex w-full justify-center py-10 text-sm text-muted-foreground"
      >
        <MediFlowLoader className="flex flex-col items-center" size={48} label={props.message} />
      </div>
    );
  }

  if (props.kind === "error") {
    return (
      <div
        role="alert"
        className="mt-4 space-y-3 rounded-xl border border-danger/40 bg-surface p-4 text-sm text-danger"
      >
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p><L10nValue>{props.message}</L10nValue></p>
          <button
            type="button"
            onClick={props.onRetry}
            className="min-h-12 rounded-lg border border-danger/40 px-4 py-2 font-medium text-danger transition-colors hover:bg-danger/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger"
          ><L10n>{"Thử lại"}</L10n></button>
        </div>
        <L10nValue>{props.correlationId ? (
          <details className="text-xs text-muted-foreground">
            <summary className="min-h-8 cursor-pointer underline underline-offset-4"><L10n>{"Thông tin hỗ trợ"}</L10n></summary>
            <p className="mt-1 break-all"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{props.correlationId}</span></p>
          </details>
        ) : null}</L10nValue>
      </div>
    );
  }

  return (
    <p role="status" className="mt-4 py-4 text-sm text-muted-foreground">
      <L10nValue>{props.message}</L10nValue>
    </p>
  );
}
