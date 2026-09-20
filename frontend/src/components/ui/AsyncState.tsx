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
        role="status"
        aria-busy="true"
        className="flex items-center gap-3 py-6 text-sm text-muted-foreground"
      >
        <MediFlowLoader size={36} label={props.message} />
      </div>
    );
  }

  if (props.kind === "error") {
    return (
      <div
        role="alert"
        className="mt-4 flex flex-col gap-3 rounded-xl border border-danger/40 bg-surface p-4 text-sm text-danger sm:flex-row sm:items-center sm:justify-between"
      >
        <div>
          <p>{props.message}</p>
          {props.correlationId ? (
            <p className="mt-1 text-xs text-muted-foreground">
              Mã theo dõi: <span className="font-mono">{props.correlationId}</span>
            </p>
          ) : null}
        </div>
        <button
          type="button"
          onClick={props.onRetry}
          className="min-h-12 rounded-lg border border-danger/40 px-4 py-2 font-medium text-danger transition-colors hover:bg-danger/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger sm:min-h-10"
        >
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <p role="status" className="mt-4 py-4 text-sm text-muted-foreground">
      {props.message}
    </p>
  );
}
