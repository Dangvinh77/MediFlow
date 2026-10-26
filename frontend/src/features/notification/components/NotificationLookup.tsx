"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import Link from "next/link";
import { useCallback, useEffect, useId, useRef, useState, useSyncExternalStore, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, getToken, subscribeToAuthChanges } from "@/lib/auth";
import { formatInstant } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { notificationApi } from "../api";
import type { NotificationDTO } from "../types";
import { channelLabels, statusPresentation } from "../presentation";
import { readNotificationPatientId } from "../patient-identity";
import { NotificationAccess, NotificationDenied, useNotificationRequestScope } from "./NotificationAccess";

const NOTIFICATION_PAGE_SIZE = 20;
const getServerRole = () => null;

interface RequestError {
  message: string;
  correlationId: string | null;
  forbidden?: boolean;
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
  return <NotificationAccess permission="lookup"><LookupWorkspace /></NotificationAccess>;
}

function LookupWorkspace() {
  const router = useRouter();
  const isCurrent = useNotificationRequestScope("lookup");
  const sequence = useRef(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const feedbackRef = useRef<HTMLDivElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const feedbackId = useId();
  const deniedPatient = useRef<{ id: string; error: RequestError } | null>(null);
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  // NotificationAccess remounts this workspace whenever the role/session changes.
  const [ownPatientId] = useState(() => role === "PATIENT" ? readNotificationPatientId(getToken()) : null);
  const [patientId, setPatientId] = useState("");
  const [activePatientId, setActivePatientId] = useState(ownPatientId ?? "");
  const [notifications, setNotifications] = useState<NotificationDTO[]>([]);
  const [loading, setLoading] = useState(Boolean(ownPatientId));
  const [searched, setSearched] = useState(Boolean(ownPatientId));
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState<NotificationRequest>({
    patientId: "",
    page: 0,
  });

  useEffect(() => {
    // Pagination/retry removes its trigger; do not interrupt someone editing the lookup.
    if (loading || !searched || document.activeElement !== document.body) return;
    if (error) feedbackRef.current?.focus();
    else headingRef.current?.focus();
  }, [loading, searched, error, notifications]);

  const loadNotifications = useCallback(
    async (id: string, page: number) => {
      if (!isCurrent()) return;
      const requestId = ++sequence.current;
      if (deniedPatient.current?.id === id) {
        setError(deniedPatient.current.error);
        setNotifications([]); setLoading(false);
        return;
      }
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
        if (!isCurrent() || requestId !== sequence.current) return;
        setNotifications(result.content);
        setPageNumber(result.number);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      } catch (cause: unknown) {
        if (!isCurrent() || requestId !== sequence.current) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }

        const nextError = cause instanceof ApiRequestError && cause.status === 403
          ? { message: "Bạn không có quyền xem thông báo của bệnh nhân này.", correlationId: cause.correlationId, forbidden: true }
          : getRequestError(cause);
        if (nextError.forbidden) deniedPatient.current = { id, error: nextError };
        setError(nextError);
      } finally {
        if (isCurrent() && requestId === sequence.current) setLoading(false);
      }
    },
    [router, isCurrent],
  );

  useEffect(() => {
    if (ownPatientId) void loadNotifications(ownPatientId, 0);
  }, [ownPatientId, loadNotifications]);

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedPatientId = patientId.trim();

    if (!isUuid(normalizedPatientId)) {
      ++sequence.current;
      setLoading(false); setNotifications([]); setSearched(false); setError(null);
      setPageNumber(0); setTotalPages(0); setTotalElements(0);
      setValidationError("Vui lòng chọn bệnh nhân hợp lệ.");
      inputRef.current?.focus();
      return;
    }

    setValidationError(null);
    setSearched(true);
    setActivePatientId(normalizedPatientId);
    void loadNotifications(normalizedPatientId, 0);
  }

  const showTable = !loading && !error && notifications.length > 0;

  if (role === "PATIENT" && !ownPatientId) {
    return <NotificationDenied message="Tài khoản chưa có hồ sơ bệnh nhân hợp lệ. Vui lòng đăng nhập lại hoặc liên hệ quản trị viên để kiểm tra hồ sơ liên kết." correlationId={null} />;
  }

  return (
    <section className="mt-6 rounded-xl border border-border bg-surface p-5 sm:p-6" aria-label="Lịch sử thông báo">
      {role === "ADMIN" ? <div className="mb-4 flex justify-end"><Link href="/notifications/send" className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground">Gửi thông báo</Link></div> : null}
      {role !== "PATIENT" ? <form
        onSubmit={onSearch}
        noValidate
        className="flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-end"
      >
        <div className="min-w-0 flex-1">
          <label
            htmlFor="notification-patient-id"
            className="mb-1 block text-sm font-medium"
          >
            Bệnh nhân
          </label>
          <ReferenceInput kind="patient"
            ref={inputRef}
            id="notification-patient-id"
            value={patientId}
            onValueChange={(selectedId) => {
              setPatientId(selectedId);
              setValidationError(null);
            }}
            placeholder="Tìm bệnh nhân theo tên"
            aria-required="true"
            aria-invalid={validationError ? true : undefined}
            aria-describedby={
              validationError ? "notification-patient-id-error" : undefined
            }
            className="min-h-12 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
          />
        </div>
        <button
          type="submit"
          disabled={!patientId.trim()}
          className="min-h-12 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-colors hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
        >
          Tra cứu thông báo
        </button>
      </form> : null}

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
        <AsyncState kind="idle" message="Chọn bệnh nhân để xem lịch sử thông báo." />
      ) : null}

      {loading ? <AsyncState kind="loading" message="Đang tải thông báo…" /> : null}

      {!loading && error ? (
        <div ref={feedbackRef} role="group" aria-label="Lỗi tải lịch sử thông báo" aria-describedby={feedbackId} tabIndex={-1}
          className="rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger">
          <div id={feedbackId}>
            {error.forbidden ? <NotificationDenied message={error.message} correlationId={error.correlationId} /> : <AsyncState
              kind="error"
              message={error.message}
              correlationId={error.correlationId}
              onRetry={() =>
                void loadNotifications(lastRequest.patientId, lastRequest.page)
              }
            />}
          </div>
        </div>
      ) : null}

      {searched && !loading && !error ? (
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <h2 ref={headingRef} tabIndex={-1} className="rounded-sm text-lg font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">{role === "PATIENT" ? "Thông báo của tôi" : "Kết quả tra cứu thông báo"}</h2>
            {role !== "PATIENT" ? <p className="mt-1 break-all text-sm text-muted-foreground">Bệnh nhân: <ReferenceText kind="patient" value={activePatientId} fallback="Bệnh nhân đã chọn" /></p> : null}
          </div>
          <button type="button" onClick={() => void loadNotifications(lastRequest.patientId, lastRequest.page)}
            className="min-h-11 rounded-lg border border-control-border px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-primary">
            Tải lại lịch sử
          </button>
        </div>
      ) : null}

      {searched && !loading && !error && notifications.length === 0 ? (
        <AsyncState kind="empty" message={role === "PATIENT" ? "Bạn chưa có thông báo." : "Bệnh nhân chưa có thông báo."} />
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
                        {(role === "ADMIN" || role === "PATIENT") ? <Link href={`/notifications/${notification.notificationId}`} className="text-primary hover:underline">{notification.title}</Link> : notification.title}
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
