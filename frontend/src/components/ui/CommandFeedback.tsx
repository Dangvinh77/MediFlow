
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import type { CommandError } from "@/lib/use-command";

export function CommandFeedback({ error, uncertain = false }: { error: CommandError | null; uncertain?: boolean }) {
  if (!error) return null;
  return (
    <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p><L10nValue>{error.message}</L10nValue></p>
      <L10nValue>{error.details.length ? <ul className="mt-2 list-inside list-disc"><L10nValue>{error.details.map((detail, index) => <li key={`${detail.field}-${index}`}>{detail.field}: <L10nValue>{detail.message}</L10nValue></li>)}</L10nValue></ul> : null}</L10nValue>
      <L10nValue>{error.code || error.correlationId ? <details className="mt-2 text-xs text-muted-foreground"><summary className="min-h-8 cursor-pointer underline underline-offset-4"><L10n>{"Thông tin hỗ trợ"}</L10n></summary><L10nValue>{error.code ? <p className="mt-1 break-all font-mono">{error.code}</p> : null}</L10nValue><L10nValue>{error.correlationId ? <p className="mt-1 break-all font-mono"><L10n>{"Mã theo dõi: "}</L10n>{error.correlationId}</p> : null}</L10nValue></details> : null}</L10nValue>
      <L10nValue>{uncertain ? <p className="mt-2"><L10n>{"Chưa xác định thao tác đã được lưu hay chưa. Kiểm tra trạng thái hiện tại trước khi gửi lại."}</L10n></p> : null}</L10nValue>
    </div>
  );
}
