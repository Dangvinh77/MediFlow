interface AdmissionMutationErrorProps {
  status: number | null;
  message: string;
  correlationId: string | null;
  onRetry?: () => void;
}

const headings: Record<number, string> = {
  400: "Dữ liệu chưa hợp lệ",
  403: "Không được phép tạo đợt nội trú",
  404: "Không tìm thấy dữ liệu tham chiếu",
  409: "Yêu cầu nội trú bị trùng hoặc xung đột",
  422: "Không thể tạo đợt nội trú",
};

export function AdmissionMutationError({
  status,
  message,
  correlationId,
  onRetry,
}: AdmissionMutationErrorProps) {
  return (
    <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p className="font-semibold">
        {status ? headings[status] ?? "Không thể xử lý yêu cầu" : "Không thể kết nối"}
      </p>
      <p className="mt-1">{message}</p>
      {correlationId ? (
        <p className="mt-2 text-xs text-muted-foreground">
          Mã theo dõi: <span className="font-mono">{correlationId}</span>
        </p>
      ) : null}
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 min-h-10 rounded-lg border border-danger/40 px-4 py-2 font-medium hover:bg-danger/10"
        >
          Thử lại
        </button>
      ) : null}
    </div>
  );
}
