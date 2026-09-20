"use client";

import { useCallback, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge, type StatusTone } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { notificationApi } from "../api";
import type {
  NotificationChannel,
  NotificationDTO,
  NotificationStatus,
} from "../types";

const NOTIFICATION_PAGE_SIZE = 20;

const channelLabels: Record<NotificationChannel, string> = {
  EMAIL: "Email",
  SMS: "SMS",
  IN_APP: "Trong ứng dụng",
};

const statusPresentation: Record<
  NotificationStatus,
  { label: string; tone: StatusTone }
> = {
  PENDING: { label: "Chờ gửi", tone: "warning" },
  SENT: { label: "Đã gửi", tone: "success" },
  FAILED: { label: "Gửi thất bại", tone: "danger" },
};

interface RequestError {
  message: string;
  correlationId: string | null;
}

interface NotificationRequest {
  patientId: string;
  page: number;
}

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return {
      message: cause.message,
      correlationId: cause.correlationId,
    };
  }

  return {
    message:
      cause instanceof Error ? cause.message : "Không thể tải thông báo.",
    correlationId: null,
  };
}

export function NotificationLookup() {
  const router = useRouter();
  const [patientId, setPatientId] = useState("");
  const [activePatientId, setActivePatientId] = useState("");
  const [notifications, setNotifications] = useState<NotificationDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState<NotificationRequest>({
    patientId: "",
    page: 0,
  });

  const loadNotifications = useCallback(
    async (id: string, page: number) => {
      setLastRequest({ patientId: id, page });
      setLoading(true);
      setError(null);
      setNotifications([]);

      try {
        const result = await notificationApi.byPatient(
          id,
          page,
          NOTIFICATION_PAGE_SIZE,
        );
        setNotifications(result.content);
        setPageNumber(result.number);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      } catch (cause: unknown) {
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }

        setError(getRequestError(cause));
      } finally {
        setLoading(false);
      }
    },
    [router],
  );

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedPatientId = patientId.trim();

    if (!isUuid(normalizedPatientId)) {
      setValidationError("Mã bệnh nhân phải là UUID hợp lệ.");
      return;
    }

    setValidationError(null);
    setSearched(true);
    setActivePatientId(normalizedPatientId);
    void loadNotifications(normalizedPatientId, 0);
  }

  const showTable = !loading && !error && notifications.length > 0;

  return (
    <section className="mt-6">
      <form
        onSubmit={onSearch}
        noValidate
        className="flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-end"
      >
        <div className="min-w-0 flex-1">
          <label
            htmlFor="notification-patient-id"
            className="mb-1 block text-sm font-medium"
          >
            Mã bệnh nhân
          </label>
          <input
            id="notification-patient-id"
            value={patientId}
            onChange={(event) => {
              setPatientId(event.target.value);
              setValidationError(null);
            }}
            placeholder="Nhập UUID bệnh nhân"
            aria-invalid={validationError ? true : undefined}
            aria-describedby={
              validationError ? "notification-patient-id-error" : undefined
            }
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
          />
        </div>
        <button
          type="submit"
          disabled={loading || !patientId.trim()}
          className="min-h-12 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-colors hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
        >
          Tra cứu thông báo
        </button>
      </form>

      {validationError ? (
        <p
          id="notification-patient-id-error"
          role="alert"
          className="mt-2 text-sm text-danger"
        >
          {validationError}
        </p>
      ) : null}

      {!searched && !validationError ? (
        <AsyncState kind="idle" message="Nhập mã bệnh nhân để xem thông báo." />
      ) : null}

      {loading ? <AsyncState kind="loading" message="Đang tải thông báo…" /> : null}

      {!loading && error ? (
        <AsyncState
          kind="error"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={() =>
            void loadNotifications(lastRequest.patientId, lastRequest.page)
          }
        />
      ) : null}

      {searched && !loading && !error && notifications.length === 0 ? (
        <AsyncState kind="empty" message="Bệnh nhân chưa có thông báo." />
      ) : null}

      {showTable ? (
        <>
          <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-[64rem] text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="px-4 py-3">
                    Tiêu đề
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Kênh
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Trạng thái
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Tạo lúc
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Gửi lúc
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Nội dung
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Lý do thất bại
                  </th>
                </tr>
              </thead>
              <tbody>
                {notifications.map((notification) => {
                  const status = statusPresentation[notification.status] ?? {
                    label: "Không xác định",
                    tone: "neutral" as const,
                  };

                  return (
                    <tr
                      key={notification.notificationId}
                      className="border-b border-border align-top last:border-0"
                    >
                      <td className="max-w-xs whitespace-normal break-words px-4 py-3 font-medium">
                        {notification.title}
                      </td>
                      <td className="px-4 py-3">
                        {channelLabels[notification.channel] ?? "Không xác định"}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3">
                        {formatInstant(notification.createdAt)}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3">
                        {formatInstant(notification.sentAt)}
                      </td>
                      <td className="max-w-md whitespace-pre-wrap break-words px-4 py-3">
                        {notification.content}
                      </td>
                      <td className="max-w-sm whitespace-normal break-words px-4 py-3">
                        {notification.failureReason ?? "—"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination
            page={pageNumber}
            totalPages={totalPages}
            totalElements={totalElements}
            loading={loading}
            onPageChange={(page) =>
              void loadNotifications(activePatientId, page)
            }
          />
        </>
      ) : null}
    </section>
  );
}
