
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
interface MedicalRecordMutationErrorProps {
  status: number | null;
  message: string;
  correlationId: string | null;
  onRetry?: () => void;
  disabled?: boolean;
}

const headings: Record<number, string> = {
  400: "Dữ liệu chưa hợp lệ",
  403: "Không được phép",
  404: "Không tìm thấy hồ sơ",
  409: "Xung đột dữ liệu",
  422: "Không thể thực hiện",
};

export function MedicalRecordMutationError({
  status,
  message,
  correlationId,
  onRetry,
  disabled = false,
}: MedicalRecordMutationErrorProps) {
  return (
    <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p className="font-semibold"><L10nValue>{status ? headings[status] ?? "Không thể xử lý yêu cầu" : "Không thể kết nối"}</L10nValue></p>
      <p className="mt-1"><L10nValue>{message}</L10nValue></p>
      <L10nValue>{correlationId ? (
        <details className="mt-2 text-xs text-muted-foreground">
          <summary className="min-h-8 cursor-pointer underline underline-offset-4"><L10n>{"Thông tin hỗ trợ"}</L10n></summary>
          <p className="mt-1 break-all font-mono"><L10n>{"Mã theo dõi: "}</L10n>{correlationId}</p>
        </details>
      ) : null}</L10nValue>
      <L10nValue>{onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          disabled={disabled}
          className="mt-3 min-h-10 rounded-lg border border-danger/40 px-4 py-2 font-medium hover:bg-danger/10 disabled:cursor-not-allowed disabled:opacity-50"
        ><L10n>{"Thử lại"}</L10n></button>
      ) : null}</L10nValue>
    </div>
  );
}
