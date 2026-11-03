import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, it, expect, vi } from "vitest";
import { webcrypto } from "node:crypto";
import { storeSession, clearSession, getSessionKey } from "@/lib/session";
import * as commandIntents from "@/lib/command-intent";
import * as labRecovery from "../lab-payment-recovery";
import * as zeroRecovery from "../lab-zero-recovery";
import { ReferenceProvider } from "@/components/references/ReferenceProvider";
import type { PaymentRequestDiscoveryDTO } from "../types";
import { LabQuotePaymentAction } from "./LabQuotePaymentAction";

const mocks = vi.hoisted(() => ({ queue: vi.fn(), discover: vi.fn(), quote: vi.fn(), pay: vi.fn(), zero: vi.fn() }));
vi.mock("../api", () => ({ billingApi: {
  paymentRequestQueue: mocks.queue, discoverPaymentRequest: mocks.discover,
  outpatientQuote: mocks.quote, createLabPayment: mocks.pay, confirmLabZeroLiability: mocks.zero,
} }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ back: vi.fn() }) }));
vi.mock("@/components/references/ReferenceInput", () => ({ ReferenceInput: ({ value, onValueChange, disabled, ...rest }: {
  value: string; onValueChange: (value: string) => void; disabled?: boolean; "aria-label"?: string;
}) => <input {...rest} value={value} disabled={disabled} onChange={event => onValueChange(event.target.value)} /> }));
import { PaymentRequestWorkspace } from "./PaymentRequestWorkspace";

const lab: PaymentRequestDiscoveryDTO = {
  paymentRequestId: "10000000-0000-4000-8000-000000000001", invoiceId: "invoice", accountId: "account",
  patientId: "10000000-0000-4000-8000-000000000002", departmentId: "department", purpose: "LAB_TEST", sourceType: "LAB_TEST",
  sourceId: "10000000-0000-4000-8000-000000000003", careEpisodeId: "10000000-0000-4000-8000-000000000004",
  careEpisodeType: "OUTPATIENT_VISIT", appointmentId: null, recordId: null, prescriptionId: null,
  labTestIds: ["10000000-0000-4000-8000-000000000003"], requestedAmount: 36000, paidAmount: 0, remainingAmount: 36000,
  currency: "VND", status: "PENDING", accountStatus: "OPEN", expiresAt: null, observedAt: "2026-11-02T03:00:00Z",
};
const rx = { ...lab, paymentRequestId: "20000000-0000-4000-8000-000000000001", purpose: "PRESCRIPTION" as const,
  sourceType: "PRESCRIPTION", sourceId: "20000000-0000-4000-8000-000000000003", labTestIds: [] };
const item = (request: PaymentRequestDiscoveryDTO) => ({ request, createdAt: "2026-11-02T03:00:00Z" });
const page = (requests = [lab, rx]) => ({ content: requests.map(item), number: 0, size: 20, totalPages: 1, totalElements: requests.length });
const directory = { canSearch: () => true, canResolve: () => true, search: vi.fn(),
  resolve: async (kind: string, id: string) => kind === "patient" ? id === lab.patientId ? "Nguyễn An · 12/04/1986" : "Trần Bình · 12/04/1987" : "Khoa Nội" };
const renderWorkspace = () => render(<ReferenceProvider directory={directory}><PaymentRequestWorkspace quoteEnabled zeroLiabilityEnabled /></ReferenceProvider>);
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }

beforeEach(() => {
  vi.restoreAllMocks();
  Object.values(mocks).forEach(mock => mock.mockReset());
  vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); clearSession();
  storeSession({ accessToken: "cashier-a", refreshToken: "refresh", role: "CASHIER" });
  mocks.queue.mockResolvedValue(page()); mocks.discover.mockResolvedValue(lab);
  mocks.quote.mockResolvedValue({ quoteId: "quote", paymentRequestId: lab.paymentRequestId, patientId: lab.patientId,
    careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: lab.careEpisodeId, currency: "VND", lines: [],
    grossAmount: "120000.00", supportAmount: "84000.00", patientPayable: "36000.00",
    issuedAt: "2026-11-02T03:00:00Z", expiresAt: "2027-11-02T03:15:00Z" });
});
afterEach(() => { vi.useRealTimers(); });

async function selectLab() { fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[0]);
  const button = await screen.findByRole("button", { name: "Xác nhận thu theo báo giá" });
  await waitFor(() => expect(button).toBeEnabled()); return button; }

it("selects a Lab item without entering source or episode codes and revalidates exact identities", async () => {
  renderWorkspace(); await selectLab();
  expect(mocks.discover).toHaveBeenCalledWith({ purpose: "LAB_TEST", sourceId: lab.sourceId, patientId: lab.patientId, careEpisodeId: lab.careEpisodeId });
  expect(screen.getByLabelText(/Mã xét nghiệm/)).not.toBeVisible();
  await waitFor(() => expect(screen.getByRole("region", { name: "Chi tiết khoản phí" })).toHaveTextContent("Nguyễn An · 12/04/1986"));
  expect(mocks.pay).not.toHaveBeenCalled();
});

it("replaces the selected-item action with a status and removes same-episode filtering when already active", async () => {
  renderWorkspace();
  await selectLab();

  expect(screen.getAllByRole("button", { name: "Chọn khoản" })).toHaveLength(1);
  expect(screen.getByRole("button", { name: "Chỉ khoản cùng lượt đã chọn" })).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Chỉ khoản cùng lượt đã chọn" }));
  await waitFor(() => expect(screen.queryByRole("button", { name: "Chỉ khoản cùng lượt đã chọn" })).not.toBeInTheDocument());
  expect(screen.getByRole("button", { name: "Xác nhận thu theo báo giá" })).toBeVisible();
});

it("shows the original quote identity on a completed receipt after recovering an older command", async () => {
  const currentQuote = { ...(await mocks.quote()), quoteId: "new-current-quote" };
  const saved = { quoteId: "old-submitted-quote", amount: "36000.00", currency: "VND", paymentMethod: "CASH" as const };
  await labRecovery.saveLabPaymentRecovery(lab.paymentRequestId, getSessionKey(), saved);
  mocks.pay.mockResolvedValue({ transactionId: "recovered-receipt", accountId: lab.accountId,
    paymentRequestId: lab.paymentRequestId, status: "COMPLETED", amount: 36000, currency: "VND",
    paymentMethod: "CASH", completedAt: lab.observedAt });

  render(<LabQuotePaymentAction paymentRequestId={lab.paymentRequestId} quote={currentQuote} quoteCurrent />);
  fireEvent.click(await screen.findByRole("button", { name: "Gửi lại cùng yêu cầu thu" }));
  await waitFor(() => expect(mocks.pay).toHaveBeenCalledTimes(1));
  expect(await screen.findByText(/Thanh toán thành công/)).toBeVisible();

  const summary = await screen.findByText("Thông tin đối soát giao dịch");
  const disclosure = summary.closest("details") as HTMLDetailsElement;
  fireEvent.click(summary);
  expect(disclosure).toHaveAttribute("open");
  expect(within(disclosure).getByText("old-submitted-quote")).toBeVisible();
  expect(within(disclosure).queryByText("new-current-quote")).not.toBeInTheDocument();
});

it("keeps the manual source lookup collapsed when queue selection is the normal path", async () => {
  renderWorkspace();
  await screen.findAllByText("Nguyễn An · 12/04/1986");
  const summary = screen.getByText("Tra cứu bằng mã được bàn giao (hỗ trợ)");
  expect(summary.closest("details")).not.toHaveAttribute("open");
});

it("blocks payment when fresh discovery no longer matches the selected queue request", async () => {
  mocks.discover.mockResolvedValue({ ...lab, paymentRequestId: rx.paymentRequestId });
  renderWorkspace(); fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[0]);
  expect(await screen.findByText(/Thông tin khoản phí.*không khớp/)).toBeVisible();
  expect(screen.queryByRole("button", { name: "Xác nhận thu theo báo giá" })).not.toBeInTheDocument();
});

it("retries a failed selected-item read without opening support codes or creating payment", async () => {
  mocks.discover.mockRejectedValueOnce(new Error("read unavailable")).mockResolvedValue(lab);
  renderWorkspace(); fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[0]);
  expect(await screen.findByText("read unavailable")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Thử kiểm tra lại" }));
  expect(await screen.findByRole("button", { name: "Xác nhận thu theo báo giá" })).toBeVisible();
  expect(screen.getByLabelText(/Mã xét nghiệm/)).not.toBeVisible();
  expect(mocks.pay).not.toHaveBeenCalled();
});

it("selects a prescription with its own source and episode instead of guessing from the patient", async () => {
  mocks.discover.mockResolvedValue(rx);
  const quote = await mocks.quote(); mocks.quote.mockResolvedValue({ ...quote, paymentRequestId: rx.paymentRequestId });
  renderWorkspace(); fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[1]);
  expect(await screen.findByRole("button", { name: "Xác nhận thanh toán" })).toBeVisible();
  expect(mocks.discover).toHaveBeenCalledWith({ purpose: "PRESCRIPTION", sourceId: rx.sourceId, patientId: rx.patientId, careEpisodeId: rx.careEpisodeId });
});

it("clears the former patient's selected detail when the patient filter changes", async () => {
  renderWorkspace(); await selectLab();
  fireEvent.change(screen.getByLabelText("Tìm bệnh nhân chờ thu"), { target: { value: "another-patient" } });
  await waitFor(() => expect(screen.queryByRole("button", { name: "Xác nhận thu theo báo giá" })).not.toBeInTheDocument());
  expect(screen.getByText(/Chọn một khoản từ hàng chờ/)).toBeVisible();
});

it("keeps the selected payment mounted and locks the queue during pending and uncertain payment", async () => {
  const pending = deferred<never>(); mocks.pay.mockReturnValue(pending.promise);
  renderWorkspace(); fireEvent.click(await selectLab());
  await waitFor(() => expect(mocks.pay).toHaveBeenCalledTimes(1));
  expect(screen.getAllByRole("button", { name: "Chọn khoản" })[0]).toBeDisabled();
  expect(screen.getByLabelText("Tìm bệnh nhân chờ thu")).toBeDisabled();
  await act(async () => pending.reject(new TypeError("Connection lost")));
  expect(await screen.findByRole("button", { name: /Gửi lại cùng yêu cầu/ })).toBeVisible();
  expect(screen.getAllByRole("button", { name: "Chọn khoản" })[0]).toBeDisabled();
  expect(mocks.discover).toHaveBeenCalledTimes(1);
});

it("discards an old queue response after changing the patient filter", async () => {
  const pending = deferred<ReturnType<typeof page>>();
  mocks.queue.mockReturnValueOnce(pending.promise).mockResolvedValue(page([{ ...rx, patientId: "other-patient" }]));
  renderWorkspace();
  fireEvent.change(screen.getByLabelText("Tìm bệnh nhân chờ thu"), { target: { value: "other-patient" } });
  expect(await screen.findByText("Trần Bình · 12/04/1987")).toBeVisible();
  await act(async () => pending.resolve(page([lab])));
  expect(screen.queryByText("Nguyễn An · 12/04/1986")).not.toBeInTheDocument();
});

it("does not run the previous cashier's post-payment read in a new signed-in session", async () => {
  const pending = deferred<unknown>(); mocks.pay.mockReturnValue(pending.promise);
  renderWorkspace(); fireEvent.click(await selectLab());
  await waitFor(() => expect(mocks.pay).toHaveBeenCalledTimes(1));
  mocks.queue.mockResolvedValue(page([{ ...rx, patientId: "new-session-patient" }]));
  act(() => storeSession({ accessToken: "cashier-b", refreshToken: "refresh-b", role: "CASHIER" }));
  expect(await screen.findByText("Trần Bình · 12/04/1987")).toBeVisible();
  await act(async () => pending.resolve({ transactionId: "old-receipt", accountId: lab.accountId,
    paymentRequestId: lab.paymentRequestId, status: "COMPLETED", amount: 36000, currency: "VND", paymentMethod: "CASH", completedAt: lab.observedAt }));
  expect(mocks.discover).toHaveBeenCalledTimes(1);
  expect(screen.queryByText(/old-receipt/)).not.toBeInTheDocument();
});

it("keeps the confirmed receipt and refreshes the queue even if the detail balance read fails", async () => {
  mocks.queue.mockResolvedValueOnce(page()).mockResolvedValue(page([rx]));
  mocks.discover.mockResolvedValueOnce(lab).mockRejectedValue(new Error("read unavailable"));
  mocks.pay.mockResolvedValue({ transactionId: "receipt", accountId: lab.accountId, paymentRequestId: lab.paymentRequestId,
    classification: "SERVICE_PAYMENT", status: "COMPLETED", amount: 36000, currency: "VND", paymentMethod: "CASH", completedAt: lab.observedAt });
  renderWorkspace(); fireEvent.click(await selectLab());
  expect(await screen.findByText(/Thanh toán thành công/)).toBeVisible();
  expect(await screen.findByText(/Chưa cập nhật được số dư sau thanh toán/)).toBeVisible();
  const supportSummary = await screen.findByText("Thông tin đối soát khoản đã chọn");
  const support = supportSummary.closest("details") as HTMLDetailsElement;
  fireEvent.click(supportSummary);
  expect(within(support).getByText("receipt")).toBeVisible();
  await waitFor(() => expect(mocks.queue).toHaveBeenCalledTimes(2));
  expect(screen.getAllByRole("button", { name: "Chọn khoản" })).toHaveLength(1);
  expect(mocks.pay).toHaveBeenCalledTimes(1);
  mocks.discover.mockResolvedValue(lab);
  fireEvent.click(screen.getByRole("button", { name: "Thử kiểm tra lại" }));
  expect(await screen.findByText(/Số dư đọc lại chưa phản ánh khoản thu/)).toBeVisible();
  expect(screen.queryByRole("button", { name: "Xác nhận thu theo báo giá" })).not.toBeInTheDocument();
  expect(screen.getByText(/Thanh toán thành công/)).toBeVisible();
});

it("locks queue selection during an uncertain zero-liability acknowledgment", async () => {
  const zeroRequest = { ...lab, requestedAmount: 0, remainingAmount: 0 };
  mocks.queue.mockResolvedValue(page([zeroRequest, rx])); mocks.discover.mockResolvedValue(zeroRequest);
  const quote = await mocks.quote(); mocks.quote.mockResolvedValue({ ...quote, patientPayable: "0.00" });
  const pending = deferred<never>(); mocks.zero.mockReturnValue(pending.promise);
  renderWorkspace(); fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[0]);
  const confirm = await screen.findByRole("button", { name: "Xác nhận nghĩa vụ 0 đồng" });
  await waitFor(() => expect(confirm).toBeEnabled()); fireEvent.click(confirm);
  await waitFor(() => expect(mocks.zero).toHaveBeenCalledTimes(1));
  expect(screen.getAllByRole("button", { name: "Chọn khoản" })[0]).toBeDisabled();
  await act(async () => pending.reject(new TypeError("Connection lost")));
  expect(await screen.findByRole("button", { name: "Gửi lại cùng yêu cầu xác nhận 0 đồng" })).toBeVisible();
  expect(screen.getAllByRole("button", { name: "Chọn khoản" })[0]).toBeDisabled();
  expect(mocks.pay).not.toHaveBeenCalled();
});

it("refreshes the queue and preserves the zero acknowledgment without creating a cash payment", async () => {
  const zeroRequest = { ...lab, requestedAmount: 0, remainingAmount: 0 };
  mocks.queue.mockResolvedValueOnce(page([zeroRequest, rx])).mockResolvedValue(page([rx]));
  mocks.discover.mockResolvedValueOnce(zeroRequest).mockResolvedValue({ ...zeroRequest, status: "PAID" });
  const quote = await mocks.quote(); mocks.quote.mockResolvedValue({ ...quote, patientPayable: "0.00" });
  mocks.zero.mockResolvedValue({ eligibilityOperationId: "zero-operation", paymentRequestId: lab.paymentRequestId,
    quoteId: "quote", basis: "ZERO_LIABILITY", clearanceId: "zero-clearance", completedAt: lab.observedAt });
  renderWorkspace(); fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[0]);
  const confirm = await screen.findByRole("button", { name: "Xác nhận nghĩa vụ 0 đồng" });
  await waitFor(() => expect(confirm).toBeEnabled()); fireEvent.click(confirm);
  expect(await screen.findByText(/Đã xác nhận nghĩa vụ 0 đồng/)).toBeVisible();
  await waitFor(() => expect(mocks.queue).toHaveBeenCalledTimes(2));
  expect(screen.getAllByRole("button", { name: "Chọn khoản" })).toHaveLength(1);
  expect(mocks.pay).not.toHaveBeenCalled();
});

it("keeps the same-payment retry visible after its quote expires during uncertainty", async () => {
  vi.useFakeTimers({ toFake: ["Date", "setInterval", "clearInterval"] });
  vi.setSystemTime(new Date("2026-11-02T03:14:50Z"));
  const quote = await mocks.quote(); mocks.quote.mockResolvedValue({ ...quote, expiresAt: "2026-11-02T03:15:00Z" });
  mocks.pay.mockRejectedValue(new TypeError("Connection lost"));
  renderWorkspace(); fireEvent.click(await selectLab());
  expect(await screen.findByRole("button", { name: /Gửi lại cùng yêu cầu/ })).toBeVisible();
  await act(async () => { await vi.advanceTimersByTimeAsync(16000); });
  expect(screen.getByRole("button", { name: /Gửi lại cùng yêu cầu/ })).toBeVisible();
  expect(screen.getAllByRole("button", { name: "Chọn khoản" })[0]).toBeDisabled();
});

it.each([false, true])("never sends a %s zero-liability command after the actor changes during preparation", async (zero) => {
  const intent = await commandIntents.prepareCommandIntent("test-deferred-intent", getSessionKey(), { test: true });
  const prepared = deferred<typeof intent>();
  const prepare = vi.spyOn(commandIntents, "prepareCommandIntent").mockReturnValueOnce(prepared.promise);
  if (zero) {
    const zeroRequest = { ...lab, requestedAmount: 0, remainingAmount: 0 };
    mocks.queue.mockResolvedValue(page([zeroRequest, rx])); mocks.discover.mockResolvedValue(zeroRequest);
    const quote = await mocks.quote(); mocks.quote.mockResolvedValue({ ...quote, patientPayable: "0.00" });
  }
  renderWorkspace();
  if (zero) {
    fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[0]);
    const button = await screen.findByRole("button", { name: "Xác nhận nghĩa vụ 0 đồng" });
    await waitFor(() => expect(button).toBeEnabled()); fireEvent.click(button);
  } else fireEvent.click(await selectLab());
  await waitFor(() => expect(prepare).toHaveBeenCalledTimes(1));
  act(() => storeSession({ accessToken: "cashier-b", refreshToken: "refresh-b", role: "CASHIER" }));
  await act(async () => prepared.resolve(intent));
  expect(mocks.pay).not.toHaveBeenCalled(); expect(mocks.zero).not.toHaveBeenCalled();
});

it.each([false, true])("keeps the new cashier's recovery after the former %s zero-liability response returns", async (zero) => {
  const pending = deferred<unknown>();
  if (zero) {
    const zeroRequest = { ...lab, requestedAmount: 0, remainingAmount: 0 };
    mocks.queue.mockResolvedValue(page([zeroRequest, rx])); mocks.discover.mockResolvedValue(zeroRequest);
    const quote = await mocks.quote(); mocks.quote.mockResolvedValue({ ...quote, patientPayable: "0.00" });
    mocks.zero.mockReturnValue(pending.promise);
  } else mocks.pay.mockReturnValue(pending.promise);
  renderWorkspace();
  if (zero) {
    fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[0]);
    const button = await screen.findByRole("button", { name: "Xác nhận nghĩa vụ 0 đồng" });
    await waitFor(() => expect(button).toBeEnabled()); fireEvent.click(button);
  } else fireEvent.click(await selectLab());
  await waitFor(() => expect(zero ? mocks.zero : mocks.pay).toHaveBeenCalledTimes(1));
  act(() => storeSession({ accessToken: "cashier-b", refreshToken: "refresh-b", role: "CASHIER" }));
  const nextSession = getSessionKey();
  const nextRecovery = { quoteId: "new-cashier-quote", amount: "99.00", currency: "VND", paymentMethod: "CASH" as const };
  if (zero) await zeroRecovery.saveLabZeroRecovery(lab.paymentRequestId, nextSession, { quoteId: nextRecovery.quoteId });
  else await labRecovery.saveLabPaymentRecovery(lab.paymentRequestId, nextSession, nextRecovery);
  await act(async () => pending.resolve(zero ? { eligibilityOperationId: "old-zero", paymentRequestId: lab.paymentRequestId,
    quoteId: "quote", basis: "ZERO_LIABILITY", clearanceId: "old-clearance", completedAt: lab.observedAt }
    : { transactionId: "old-payment", accountId: lab.accountId, paymentRequestId: lab.paymentRequestId,
      status: "COMPLETED", amount: 36000, currency: "VND", paymentMethod: "CASH", completedAt: lab.observedAt }));
  expect(zero ? await zeroRecovery.readLabZeroRecovery(lab.paymentRequestId, nextSession)
    : await labRecovery.readLabPaymentRecovery(lab.paymentRequestId, nextSession)).toEqual(zero ? { quoteId: nextRecovery.quoteId } : nextRecovery);
});

it.each([false, true])("preserves the original %s zero-liability recovery when a retry is aborted before redispatch", async (zero) => {
  const actorA = getSessionKey();
  const saved = { quoteId: "quote", amount: "36000.00", currency: "VND", paymentMethod: "CASH" as const };
  const body = zero ? { quoteId: saved.quoteId } : saved;
  const scope = `${zero ? "lab-zero-liability" : "lab-payment"}:${lab.paymentRequestId}`;
  const originalIntent = await commandIntents.prepareCommandIntent(scope, actorA, body);
  const gate = deferred<void>();
  if (zero) {
    const zeroRequest = { ...lab, requestedAmount: 0, remainingAmount: 0 };
    mocks.queue.mockResolvedValue(page([zeroRequest, rx])); mocks.discover.mockResolvedValue(zeroRequest);
    const quote = await mocks.quote(); mocks.quote.mockResolvedValue({ ...quote, patientPayable: "0.00" });
    await zeroRecovery.saveLabZeroRecovery(lab.paymentRequestId, actorA, { quoteId: saved.quoteId });
  } else await labRecovery.saveLabPaymentRecovery(lab.paymentRequestId, actorA, saved);
  const originalLabSave = labRecovery.saveLabPaymentRecovery, originalZeroSave = zeroRecovery.saveLabZeroRecovery;
  const save = zero ? vi.spyOn(zeroRecovery, "saveLabZeroRecovery").mockImplementationOnce(async (...args) => {
    await gate.promise; await originalZeroSave(...args);
  }) : vi.spyOn(labRecovery, "saveLabPaymentRecovery").mockImplementationOnce(async (...args) => {
    await gate.promise; await originalLabSave(...args);
  });
  renderWorkspace(); fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[0]);
  fireEvent.click(await screen.findByRole("button", { name: /Gửi lại cùng yêu cầu/ }));
  await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
  act(() => storeSession({ accessToken: "cashier-b", refreshToken: "refresh-b", role: "CASHIER" }));
  await act(async () => gate.resolve());
  expect(mocks.pay).not.toHaveBeenCalled(); expect(mocks.zero).not.toHaveBeenCalled();
  expect(zero ? await zeroRecovery.readLabZeroRecovery(lab.paymentRequestId, actorA)
    : await labRecovery.readLabPaymentRecovery(lab.paymentRequestId, actorA)).toEqual(body);
  expect((await commandIntents.prepareCommandIntent(scope, actorA, body)).key).toBe(originalIntent.key);
});

it("keeps the original uncertain payment locked when saving its replay fails", async () => {
  const actor = getSessionKey();
  const saved = { quoteId: "quote", amount: "36000.00", currency: "VND", paymentMethod: "CASH" as const };
  const original = await commandIntents.prepareCommandIntent(`lab-payment:${lab.paymentRequestId}`, actor, saved);
  await labRecovery.saveLabPaymentRecovery(lab.paymentRequestId, actor, saved);
  vi.spyOn(labRecovery, "saveLabPaymentRecovery").mockRejectedValue(new Error("storage unavailable"));
  renderWorkspace(); fireEvent.click((await screen.findAllByRole("button", { name: "Chọn khoản" }))[0]);
  fireEvent.click(await screen.findByRole("button", { name: /Gửi lại cùng yêu cầu/ }));
  expect(await screen.findByText("storage unavailable")).toBeVisible();
  expect(screen.getAllByRole("button", { name: "Chọn khoản" })[0]).toBeDisabled();
  expect(screen.getByRole("button", { name: /Gửi lại cùng yêu cầu/ })).toBeVisible();
  expect(mocks.pay).not.toHaveBeenCalled();
  expect(await labRecovery.readLabPaymentRecovery(lab.paymentRequestId, actor)).toEqual(saved);
  expect((await commandIntents.prepareCommandIntent(`lab-payment:${lab.paymentRequestId}`, actor, saved)).key).toBe(original.key);
});
