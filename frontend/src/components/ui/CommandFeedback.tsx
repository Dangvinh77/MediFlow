import type { CommandError } from "@/lib/use-command";

export function CommandFeedback({ error, uncertain = false }: { error: CommandError | null; uncertain?: boolean }) {
  if (!error) return null;
  return (
    <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p>{error.message}</p>
      {error.code ? <p className="mt-1 break-all font-mono text-xs">{error.code}</p> : null}
      {error.details.length ? <ul className="mt-2 list-inside list-disc">{error.details.map((detail, index) => <li key={`${detail.field}-${index}`}>{detail.field}: {detail.message}</li>)}</ul> : null}
      {error.correlationId ? <p className="mt-2 break-all text-xs">Mã theo dõi: {error.correlationId}</p> : null}
      {uncertain ? <p className="mt-2">Chưa xác định thao tác đã được lưu hay chưa. Kiểm tra trạng thái hiện tại trước khi gửi lại.</p> : null}
    </div>
  );
}
