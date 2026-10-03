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
      <p>{message}</p>
      {correlationId ? (
        <p className="mt-1 text-xs text-muted-foreground">
          Mã theo dõi: <span className="font-mono">{correlationId}</span>
        </p>
      ) : null}
    </div>
  );
}
