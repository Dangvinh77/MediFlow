
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
interface AppointmentMutationErrorProps {
  message: string;
  correlationId: string | null;
}

export function AppointmentMutationError({
  message,
  correlationId,
}: AppointmentMutationErrorProps) {
  return (
    <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p><L10nValue>{message}</L10nValue></p>
      <L10nValue>{correlationId ? (
        <p className="mt-1 text-xs text-muted-foreground"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{correlationId}</span>
        </p>
      ) : null}</L10nValue>
    </div>
  );
}
