import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import type { NotificationDTO } from "../types";

const mocks = vi.hoisted(() => ({
  role: "ADMIN" as Role | null, token: "session-a", listeners: new Set<() => void>(),
  list: vi.fn(), detail: vi.fn(), send: vi.fn(), router: { push: vi.fn(), replace: vi.fn() },
}));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role, getToken: () => mocks.token,
  subscribeToAuthChanges: (listener: () => void) => { mocks.listeners.add(listener); return () => mocks.listeners.delete(listener); },
}));
vi.mock("../api", () => ({ notificationApi: { byPatient: mocks.list, getById: mocks.detail, send: mocks.send } }));
import { NotificationLookup } from "./NotificationLookup";
import { NotificationDetail } from "./NotificationDetail";
import { SendNotificationForm } from "./SendNotificationForm";

const id = "00000000-0000-4000-8000-000000000001";
const otherId = "00000000-0000-4000-8000-000000000002";
const notice: NotificationDTO = { notificationId: id, patientId: id, title: "Nhắc lịch khám", content: "Đến đúng giờ",
  channel: "IN_APP", status: "SENT", failureReason: null, createdAt: "2026-10-18T00:00:00Z", sentAt: "2026-10-18T00:00:01Z" };
function page(content = [notice], number = 0) { return { content, number, size: 20, totalPages: 2, totalElements: 21 }; }
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: unknown) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }
function fill(label: string, value: string) { fireEvent.change(screen.getByLabelText(label, { exact: false }), { target: { value } }); }
function search(patient = id) { fill("Mã bệnh nhân", patient); fireEvent.submit(screen.getByLabelText("Mã bệnh nhân").closest("form")!); }
function submit() { fireEvent.submit(screen.getByLabelText("Tiêu đề", { exact: false }).closest("form")!); }
function fillSend() { fill("Mã bệnh nhân", id); fill("Tiêu đề", " Nhắc lịch khám "); fill("Nội dung", " Đến đúng giờ "); }
function changeSession(role: Role = "ADMIN") { act(() => { mocks.token = "session-b"; mocks.role = role; mocks.listeners.forEach(listener => listener()); }); }

beforeEach(() => {
  mocks.role = "ADMIN"; mocks.token = "session-a"; mocks.listeners.clear();
  mocks.list.mockReset().mockResolvedValue(page()); mocks.detail.mockReset().mockResolvedValue(notice);
  mocks.send.mockReset().mockResolvedValue(notice); mocks.router.push.mockReset(); mocks.router.replace.mockReset();
});

describe("Notification patient history", () => {
  it("refreshes the submitted patient and current page, without resending or using the edited draft", async () => {
    const user = userEvent.setup();
    mocks.list.mockResolvedValueOnce(page()).mockResolvedValueOnce(page([notice], 1))
      .mockResolvedValueOnce(page([{ ...notice, status: "FAILED", failureReason: "Không thể giao" }], 1));
    render(<NotificationLookup />); search(); await screen.findByText(notice.title);
    await user.click(screen.getByRole("button", { name: "Trang sau" }));
    await screen.findByText("Trang 2/2", { exact: false });
    fill("Mã bệnh nhân", otherId);
    await user.click(screen.getByRole("button", { name: "Tải lại lịch sử" }));
    expect(await screen.findByText("Gửi thất bại")).toBeVisible();
    expect(screen.getByText(`Bệnh nhân: ${id}`)).toBeVisible();
    expect(screen.getByRole("heading", { name: "Kết quả tra cứu thông báo" })).toHaveFocus();
    expect(mocks.list).toHaveBeenLastCalledWith(id, 1, 20);
    expect(mocks.list).toHaveBeenCalledTimes(3); expect(mocks.send).not.toHaveBeenCalled();
  });
  it("can refresh an empty history to discover a new notification", async () => {
    mocks.list.mockResolvedValueOnce(page([]));
    render(<NotificationLookup />); search(); await screen.findByText("Bệnh nhân chưa có thông báo.");
    fireEvent.click(screen.getByRole("button", { name: "Tải lại lịch sử" }));
    expect(await screen.findByText(notice.title)).toBeVisible();
    expect(mocks.list).toHaveBeenLastCalledWith(id, 0, 20); expect(mocks.send).not.toHaveBeenCalled();
  });
  it("clears old history while refreshing and preserves focus in the patient draft", async () => {
    const pending = deferred<ReturnType<typeof page>>();
    mocks.list.mockResolvedValueOnce(page()).mockReturnValueOnce(pending.promise);
    render(<NotificationLookup />); search(); await screen.findByText(notice.title);
    fireEvent.click(screen.getByRole("button", { name: "Tải lại lịch sử" }));
    expect(screen.queryByText(notice.content)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tải lại lịch sử" })).not.toBeInTheDocument();
    const input = screen.getByLabelText("Mã bệnh nhân"); input.focus(); fill("Mã bệnh nhân", otherId);
    await act(async () => pending.resolve(page()));
    expect(input).toHaveFocus(); expect(input).toHaveValue(otherId);
    expect(screen.getByText(`Bệnh nhân: ${id}`)).toBeVisible();
  });
  it.each([403, 503])("focuses history refresh error %i with correlation and respects retry policy", async status => {
    const user = userEvent.setup();
    mocks.list.mockResolvedValueOnce(page()).mockRejectedValueOnce(new ApiRequestError("Unavailable", status, "READ_ERROR", [], "history-refresh"));
    render(<NotificationLookup />); search(); await screen.findByText(notice.title);
    await user.click(screen.getByRole("button", { name: "Tải lại lịch sử" }));
    const feedback = await screen.findByRole("group", { name: "Lỗi tải lịch sử thông báo" });
    expect(feedback).toHaveFocus(); expect(feedback).toHaveAccessibleDescription(/history-refresh/);
    expect(screen.queryByText(notice.content)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tải lại lịch sử" })).not.toBeInTheDocument();
    if (status === 403) {
      expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
      search(); expect(mocks.list).toHaveBeenCalledTimes(2);
    } else {
      await user.tab(); expect(screen.getByRole("button", { name: "Thử lại" })).toHaveFocus();
      await user.keyboard("{Enter}");
      expect(await screen.findByRole("heading", { name: "Kết quả tra cứu thông báo" })).toHaveFocus();
      expect(mocks.list).toHaveBeenCalledTimes(3);
    }
    expect(mocks.send).not.toHaveBeenCalled();
  });
  it("discards a history refresh that finishes after session change", async () => {
    const pending = deferred<ReturnType<typeof page>>();
    mocks.list.mockResolvedValueOnce(page()).mockReturnValueOnce(pending.promise);
    render(<NotificationLookup />); search(); await screen.findByText(notice.title);
    fireEvent.click(screen.getByRole("button", { name: "Tải lại lịch sử" })); changeSession();
    await act(async () => pending.resolve(page()));
    expect(screen.getByLabelText("Mã bệnh nhân")).toHaveValue("");
    expect(screen.queryByText(notice.content)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tải lại lịch sử" })).not.toBeInTheDocument();
  });
  it.each(["DOCTOR", "PHARMACIST", "MANAGER", null] as const)("blocks role %s before mounting a reader", role => {
    mocks.role = role; render(<NotificationLookup />);
    expect(screen.queryByLabelText("Mã bệnh nhân")).not.toBeInTheDocument(); expect(mocks.list).not.toHaveBeenCalled();
  });
  it("validates UUID then paginates using the submitted patient, with privacy-safe translated values", async () => {
    render(<NotificationLookup />); search("bad");
    expect(screen.getByRole("alert")).toHaveTextContent("UUID"); expect(mocks.list).not.toHaveBeenCalled();
    search(` ${id} `); expect(await screen.findByRole("link", { name: notice.title })).toHaveAttribute("href", `/notifications/${id}`);
    expect(screen.getByText("Trong ứng dụng")).toBeVisible(); expect(screen.getByText("Đã gửi")).toBeVisible();
    fill("Mã bệnh nhân", otherId); fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await waitFor(() => expect(mocks.list).toHaveBeenLastCalledWith(id, 1, 20));
  });
  it("focuses an invalid search and suppresses an older pending response", async () => {
    const pending = deferred<ReturnType<typeof page>>(); mocks.list.mockReturnValueOnce(pending.promise);
    render(<NotificationLookup />); search(); search("invalid");
    expect(screen.getByLabelText("Mã bệnh nhân")).toHaveFocus();
    await act(async () => pending.resolve(page()));
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Trang sau" })).not.toBeInTheDocument();
    expect(mocks.list).toHaveBeenCalledTimes(1);
  });
  it("keeps NURSE list-only, without send or detail links", async () => {
    mocks.role = "NURSE"; render(<NotificationLookup />); search();
    await screen.findByText(notice.title);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
  it("uses latest query only, including ignoring a stale 401", async () => {
    const old = deferred<ReturnType<typeof page>>(); mocks.list.mockReturnValueOnce(old.promise);
    render(<NotificationLookup />); search(); search(otherId);
    await screen.findByText(notice.title);
    await act(async () => old.reject(new ApiRequestError("Old session", 401)));
    expect(screen.getByText(notice.title)).toBeVisible(); expect(mocks.router.replace).not.toHaveBeenCalled();
  });
  it("does not offer retry for cross-patient denial, but permits a new patient search", async () => {
    mocks.role = "PATIENT";
    mocks.list.mockRejectedValueOnce(new ApiRequestError("Denied", 403, "FORBIDDEN", [], "history-trace"));
    render(<NotificationLookup />); search();
    expect(await screen.findByRole("alert")).toHaveTextContent("history-trace");
    expect(screen.getByRole("alert")).toHaveTextContent("không có quyền");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    search(); expect(mocks.list).toHaveBeenCalledTimes(1);
    search(otherId); await screen.findByText(notice.title);
    expect(mocks.list).toHaveBeenLastCalledWith(otherId, 0, 20);
  });
  it("retries an unavailable history using its submitted patient rather than the edited draft", async () => {
    mocks.list.mockRejectedValueOnce(new ApiRequestError("Unavailable", 503));
    render(<NotificationLookup />); search(); await screen.findByRole("alert");
    fill("Mã bệnh nhân", otherId); fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText(notice.title); expect(mocks.list).toHaveBeenLastCalledWith(id, 0, 20);
  });
  it("clears private history on session change and ignores old completion after unmount", async () => {
    const pending = deferred<ReturnType<typeof page>>(); mocks.list.mockReturnValueOnce(pending.promise);
    const view = render(<NotificationLookup />); search(); changeSession();
    expect(screen.getByLabelText("Mã bệnh nhân")).toHaveValue("");
    await act(async () => pending.resolve(page())); expect(screen.queryByText(notice.title)).not.toBeInTheDocument();
    const late = deferred<ReturnType<typeof page>>(); mocks.list.mockReturnValueOnce(late.promise); search(); view.unmount();
    await act(async () => late.reject(new ApiRequestError("Late", 401))); expect(mocks.router.replace).not.toHaveBeenCalled();
  });
  it("shows empty results distinctly and redirects a current 401", async () => {
    mocks.list.mockResolvedValueOnce(page([])); render(<NotificationLookup />); search();
    expect(await screen.findByText("Bệnh nhân chưa có thông báo.")).toBeVisible();
    mocks.list.mockRejectedValueOnce(new ApiRequestError("Expired", 401)); search();
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
  });
});

describe("Notification detail", () => {
  it("offers an explicit read refresh and focuses the updated result without resending", async () => {
    const user = userEvent.setup();
    mocks.detail.mockResolvedValueOnce({ ...notice, status: "PENDING", sentAt: null });
    render(<NotificationDetail notificationId={id} />);
    await screen.findByText(notice.title);
    expect(mocks.detail).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole("button", { name: "Tải lại thông báo" }));
    await screen.findByText("Đã gửi");
    expect(screen.getByRole("heading", { name: notice.title })).toHaveFocus();
    expect(mocks.detail).toHaveBeenCalledTimes(2);
    expect(mocks.detail).toHaveBeenLastCalledWith(id);
    expect(mocks.send).not.toHaveBeenCalled();
  });
  it.each([403, 503])("focuses named detail error feedback for HTTP %i", async status => {
    mocks.detail.mockRejectedValueOnce(new ApiRequestError("Unavailable", status, "READ_ERROR", [], "detail-focus"));
    render(<NotificationDetail notificationId={id} />);
    const feedback = await screen.findByRole("group", { name: "Lỗi tải thông báo" });
    await waitFor(() => expect(feedback).toHaveFocus());
    expect(feedback).toHaveAccessibleDescription(/detail-focus/);
    if (status === 403) expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });
  it("preserves focus outside detail when a late read fails", async () => {
    const pending = deferred<NotificationDTO>(); mocks.detail.mockReturnValueOnce(pending.promise);
    render(<><button>Điều hướng khác</button><NotificationDetail notificationId={id} /></>);
    const outside = screen.getByRole("button", { name: "Điều hướng khác" }); outside.focus();
    await act(async () => pending.reject(new ApiRequestError("Unavailable", 503)));
    expect(await screen.findByRole("alert")).toBeVisible();
    expect(outside).toHaveFocus();
  });
  it("supports Tab and Enter from error feedback to a successful read retry", async () => {
    const user = userEvent.setup();
    mocks.detail.mockRejectedValueOnce(new ApiRequestError("Unavailable", 503));
    render(<NotificationDetail notificationId={id} />);
    const feedback = await screen.findByRole("group", { name: "Lỗi tải thông báo" });
    await waitFor(() => expect(feedback).toHaveFocus());
    await user.tab();
    expect(screen.getByRole("button", { name: "Thử lại" })).toHaveFocus();
    await user.keyboard("{Enter}");
    expect(await screen.findByRole("heading", { name: notice.title })).toHaveFocus();
    expect(mocks.detail).toHaveBeenCalledTimes(2);
    expect(mocks.send).not.toHaveBeenCalled();
  });
  it("clears private detail during refresh and keeps focus outside when it settles", async () => {
    const pending = deferred<NotificationDTO>(); mocks.detail.mockReturnValueOnce(Promise.resolve(notice)).mockReturnValueOnce(pending.promise);
    render(<><button>Điều hướng khác</button><NotificationDetail notificationId={id} /></>);
    await screen.findByText(notice.title);
    fireEvent.click(screen.getByRole("button", { name: "Tải lại thông báo" }));
    expect(screen.queryByText(notice.content)).not.toBeInTheDocument();
    const outside = screen.getByRole("button", { name: "Điều hướng khác" }); outside.focus();
    await act(async () => pending.resolve(notice));
    expect(outside).toHaveFocus();
    expect(mocks.detail).toHaveBeenCalledTimes(2);
  });
  it("does not restore a refreshed private result after the session changes", async () => {
    const pending = deferred<NotificationDTO>(); mocks.detail.mockResolvedValueOnce(notice).mockReturnValueOnce(pending.promise).mockResolvedValueOnce({ ...notice, title: "Phiên mới" });
    render(<NotificationDetail notificationId={id} />); await screen.findByText(notice.title);
    fireEvent.click(screen.getByRole("button", { name: "Tải lại thông báo" }));
    changeSession(); await screen.findByText("Phiên mới");
    await act(async () => pending.resolve(notice));
    expect(screen.queryByText(notice.title)).not.toBeInTheDocument();
    expect(screen.getByText("Phiên mới")).toBeVisible();
  });
  it("rejects invalid IDs and disallowed roles without requests", () => {
    const view = render(<NotificationDetail notificationId="invalid" />); expect(mocks.detail).not.toHaveBeenCalled();
    view.unmount(); mocks.role = "NURSE"; render(<NotificationDetail notificationId={id} />); expect(mocks.detail).not.toHaveBeenCalled();
  });
  it("renders only public fields, status/channel labels and failure reason", async () => {
    mocks.detail.mockResolvedValue({ ...notice, channel: "EMAIL", status: "FAILED", failureReason: "Không thể giao", recipientAddress: "private@example.com", retryCount: 12 });
    render(<NotificationDetail notificationId={id} notice="sent" />);
    expect(await screen.findByText("Gửi thất bại")).toBeVisible(); expect(screen.getByText("Không thể giao")).toBeVisible();
    expect(screen.getByText("Email")).toBeVisible(); expect(screen.queryByText("private@example.com")).not.toBeInTheDocument();
    expect(screen.queryByText(/retryCount/)).not.toBeInTheDocument();
  });
  it("clears the previous notification when ID changes and ignores old responses", async () => {
    const old = deferred<NotificationDTO>(); mocks.detail.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ ...notice, notificationId: otherId, title: "Mới" });
    const view = render(<NotificationDetail notificationId={id} />); view.rerender(<NotificationDetail notificationId={otherId} />);
    await screen.findByText("Mới"); await act(async () => old.resolve(notice));
    expect(screen.queryByText(notice.title)).not.toBeInTheDocument(); expect(screen.getByText("Mới")).toBeVisible();
  });
  it("keeps a forbidden detail closed while offering navigation to history", async () => {
    mocks.detail.mockRejectedValueOnce(new ApiRequestError("Denied", 403, "FORBIDDEN", [], "detail-denied"));
    render(<NotificationDetail notificationId={id} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("detail-denied");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Quay lại tra cứu" })).toHaveAttribute("href", "/notifications");
    expect(mocks.detail).toHaveBeenCalledTimes(1);
  });
  it.each([404, 503])("retries a %i error without reloading the whole page", async status => {
    mocks.detail.mockRejectedValueOnce(new ApiRequestError("Unavailable", status, "READ_ERROR", [], "detail-trace"));
    render(<NotificationDetail notificationId={id} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("detail-trace");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" })); await screen.findByText(notice.title);
    expect(mocks.detail).toHaveBeenCalledTimes(2);
  });
});

describe("Notification manual send", () => {
  it("focuses the earliest visible field rather than the order of server details", async () => {
    mocks.send.mockRejectedValue(new ApiRequestError("Validation", 400, "VALIDATION_ERROR", [
      { field: "content", message: "Kiểm tra nội dung" },
      { field: "title", message: "Kiểm tra tiêu đề" },
    ]));
    render(<SendNotificationForm />); fillSend(); submit();
    await waitFor(() => expect(screen.getByLabelText("Tiêu đề", { exact: false })).toHaveFocus());
  });
  it.each([403, 503, 400])("focuses form feedback for HTTP %s without dispatching another send", async status => {
    mocks.send.mockRejectedValue(new ApiRequestError("Request failed", status, "COMMAND_REJECTED", [], "send-focus"));
    render(<SendNotificationForm />); fillSend(); submit();
    const alert = await screen.findByRole("alert");
    await waitFor(() => expect(alert).toHaveFocus());
    expect(alert).toHaveTextContent("send-focus");
    expect(mocks.send).toHaveBeenCalledTimes(1);
    if (status !== 400) expect(screen.getByRole("button", { name: "Gửi thông báo" })).toBeDisabled();
  });
  it("locks a server-denied command including direct form resubmission", async () => {
    mocks.send.mockRejectedValue(new ApiRequestError("Denied", 403, "FORBIDDEN", [], "send-denied"));
    render(<SendNotificationForm />); fillSend(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("send-denied");
    expect(screen.getByRole("button", { name: "Gửi thông báo" })).toBeDisabled();
    submit(); expect(mocks.send).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Quay lại tra cứu" })).toBeEnabled();
  });
  it.each(["PATIENT", "NURSE", "DOCTOR", null] as const)("denies role %s", role => {
    mocks.role = role; render(<SendNotificationForm />); expect(screen.queryByRole("button", { name: "Gửi thông báo" })).not.toBeInTheDocument();
    expect(mocks.send).not.toHaveBeenCalled();
  });
  it("validates required fields, focuses first error and checks channel-specific address", async () => {
    render(<SendNotificationForm />); submit(); expect(screen.getByLabelText("Mã bệnh nhân", { exact: false })).toHaveFocus();
    fillSend(); fill("Kênh", "SMS"); fill("Địa chỉ người nhận", "+84912345678"); submit();
    await waitFor(() => expect(screen.getByLabelText("Địa chỉ người nhận", { exact: false })).toHaveFocus());
    expect(mocks.send).not.toHaveBeenCalled();
  });
  it("does not move keyboard focus to an old field while editing the next input", async () => {
    render(<SendNotificationForm />); submit();
    const patient = screen.getByLabelText("Mã bệnh nhân", { exact: false });
    fill("Mã bệnh nhân", id);
    const title = screen.getByLabelText("Tiêu đề", { exact: false }); title.focus(); fill("Tiêu đề", "Nhắc lịch");
    await waitFor(() => expect(title).toHaveFocus()); expect(patient).not.toHaveFocus();
  });
  it("locks duplicate submit and editing, sends exact fields, and locks again after success", async () => {
    const pending = deferred<NotificationDTO>(); mocks.send.mockReturnValueOnce(pending.promise);
    render(<SendNotificationForm />); fillSend(); act(() => { submit(); submit(); });
    expect(mocks.send).toHaveBeenCalledExactlyOnceWith({ patientId: id, title: notice.title, content: notice.content, channel: "IN_APP", recipientAddress: null });
    expect(screen.getByLabelText("Tiêu đề", { exact: false })).toBeDisabled();
    await act(async () => pending.resolve(notice));
    expect(mocks.router.push).toHaveBeenCalledWith(`/notifications/${id}?notice=sent`);
    submit(); expect(mocks.send).toHaveBeenCalledTimes(1);
  });
  it("clears address when switching channels and never sends an old email as IN_APP", async () => {
    render(<SendNotificationForm />); fillSend(); fill("Kênh", "EMAIL"); fill("Địa chỉ người nhận", "private@example.com"); fill("Kênh", "IN_APP");
    expect(screen.getByLabelText("Địa chỉ người nhận", { exact: false })).toHaveValue(""); submit();
    await waitFor(() => expect(mocks.send).toHaveBeenCalledWith(expect.objectContaining({ recipientAddress: null })));
  });
  it.each([new TypeError("Network"), new ApiRequestError("Unavailable", 503), new ApiRequestError("Empty", 200)])("does not resend an uncertain notification: %s", async cause => {
    mocks.send.mockRejectedValueOnce(cause); render(<SendNotificationForm />); fillSend(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("tra cứu lịch sử bệnh nhân");
    expect(screen.getByRole("button", { name: "Gửi thông báo" })).toBeDisabled(); submit(); expect(mocks.send).toHaveBeenCalledTimes(1);
  });
  it("preserves server field errors and correlation, then focuses after unlocking", async () => {
    mocks.send.mockRejectedValueOnce(new ApiRequestError("Validation", 400, "VALIDATION_ERROR", [{ field: "title", message: "Tiêu đề không phù hợp" }], "send-trace"));
    render(<SendNotificationForm />); fillSend(); submit();
    await waitFor(() => expect(screen.getByLabelText("Tiêu đề", { exact: false })).toHaveFocus());
    expect(screen.getByText(/send-trace/)).toBeVisible(); expect(screen.getByText("Tiêu đề không phù hợp")).toBeVisible();
  });
  it.each([408, 504])("locks notification HTTP %s for history reconciliation instead of treating it as validation", async status => {
    mocks.send.mockRejectedValueOnce(new ApiRequestError("Timeout", status, "REQUEST_TIMEOUT", [{ field: "title", message: "Chưa xác nhận tiêu đề" }], "send-timeout"));
    render(<SendNotificationForm />); fillSend(); submit();
    const alert = await screen.findByRole("alert"); await waitFor(() => expect(alert).toHaveFocus());
    expect(alert).toHaveTextContent("send-timeout"); expect(alert).toHaveTextContent("tra cứu lịch sử bệnh nhân");
    expect(screen.getByLabelText("Tiêu đề", { exact: false })).toBeDisabled();
    expect(screen.getByLabelText("Tiêu đề", { exact: false })).not.toHaveAttribute("aria-invalid", "true");
    submit(); expect(mocks.send).toHaveBeenCalledTimes(1); expect(mocks.router.push).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu lịch sử bệnh nhân" }));
    expect(mocks.router.push).toHaveBeenCalledWith("/notifications"); expect(mocks.send).toHaveBeenCalledTimes(1);
  });
  it("rechecks role at submit and ignores success after actor change", async () => {
    const pending = deferred<NotificationDTO>(); mocks.send.mockReturnValueOnce(pending.promise);
    render(<SendNotificationForm />); fillSend(); mocks.role = "NURSE"; submit(); expect(mocks.send).not.toHaveBeenCalled();
    mocks.role = "ADMIN"; submit(); changeSession();
    expect(screen.getByLabelText("Tiêu đề", { exact: false })).toHaveValue("");
    await act(async () => pending.resolve(notice)); expect(mocks.router.push).not.toHaveBeenCalled();
  });
  it("redirects a current unauthorized session", async () => {
    mocks.send.mockRejectedValueOnce(new ApiRequestError("Expired", 401)); render(<SendNotificationForm />); fillSend(); submit();
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
  });
});
