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
      <p className="font-semibold">{status ? headings[status] ?? "Không thể xử lý yêu cầu" : "Không thể kết nối"}</p>
      <p className="mt-1">{message}</p>
      {correlationId ? (
        <details className="mt-2 text-xs text-muted-foreground">
          <summary className="min-h-8 cursor-pointer underline underline-offset-4">Thông tin hỗ trợ</summary>
          <p className="mt-1 break-all font-mono">Mã theo dõi: {correlationId}</p>
        </details>
      ) : null}
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          disabled={disabled}
          className="mt-3 min-h-10 rounded-lg border border-danger/40 px-4 py-2 font-medium hover:bg-danger/10 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Thử lại
        </button>
      ) : null}
    </div>
  );
}
