import { AsyncState } from "@/components/ui/AsyncState";
import type { ReportRequestState } from "../useReportRequest";

interface ReportRequestFeedbackProps {
  state: ReportRequestState<unknown>;
  idle: string;
  loading: string;
  onRetry: () => void;
}

export function ReportRequestFeedback({ state, idle, loading, onRetry }: ReportRequestFeedbackProps) {
  if (state.status === "idle") return <AsyncState kind="idle" message={idle} />;
  if (state.status === "loading") return <AsyncState kind="loading" message={loading} />;
  if (state.status !== "error") return null;
  return (
    <div>
      {state.forbidden ? (
        <div role="alert" className="mt-5 rounded-xl border border-danger/40 bg-surface p-4 text-sm">
          <p className="text-danger">{state.message}</p>
          {state.correlationId && <p className="mt-1 break-all text-xs text-muted-foreground">Mã theo dõi: {state.correlationId}</p>}
        </div>
      ) : <AsyncState kind="error" message={state.message} correlationId={state.correlationId} onRetry={onRetry} />}
      {state.code && <p className="mt-2 break-all text-xs text-muted-foreground">Mã lỗi: {state.code}</p>}
    </div>
  );
}
