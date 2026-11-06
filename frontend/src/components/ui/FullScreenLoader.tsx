import { MediFlowLoader } from "@mediflow/loader";

/** Covers the whole viewport, for moments when there is no page to show yet, such as signing in. */
export function FullScreenLoader({ label }: { label: string }) {
  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-background text-sm text-muted-foreground"
    >
      <MediFlowLoader className="flex flex-col items-center" size={96} label={label} />
    </div>
  );
}
