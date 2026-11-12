"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { ReferenceText } from "@/components/references/ReferenceText";
import { PatientContextBanner } from "@/components/references/PatientContextBanner";
import Link from "next/link";
import { FinancialStatus } from "./FinancialStatus";
import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { StatusBadge, type StatusTone } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { labApi } from "../api";
import type { LabReportDTO, LabTestDTO, LabTestStatus } from "../types";
import { LabActions } from "./LabActions";
import { LabReportWorkflowPanel } from "./LabReportWorkflowPanel";
import { SpecimenPanel } from "./SpecimenPanel";
import { safeLabReturn } from "../navigation";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";

interface LabDetailProps {
  testId: string;
  notice?: "created";
  returnTo?: string;
}

interface RequestError {
  message: string;
  correlationId: string | null;
}

/** disabled: review flag off (v1 entry only); none: flag on, no report yet; report: the test is in the workflow. */
type ReviewState =
  | { status: "loading" }
  | { status: "disabled" }
  | { status: "none" }
  | { status: "report"; report: LabReportDTO };

type DetailState =
  | { key: string; status: "idle" }
  | { key: string; status: "success"; test: LabTestDTO }
  | { key: string; status: "not-found" }
  | { key: string; status: "error"; error: RequestError };

const statusPresentation: Record<LabTestStatus, { label: string; tone: StatusTone }> = {
  PENDING: { label: "Chờ xử lý", tone: "warning" },
  AWAITING_PAYMENT: { label: "Chờ xác nhận thanh toán", tone: "warning" },
  READY: { label: "Sẵn sàng", tone: "info" },
  IN_PROGRESS: { label: "Đang thực hiện", tone: "info" },
  COMPLETED: { label: "Đã hoàn tất", tone: "success" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
};

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải chi tiết xét nghiệm.",
    correlationId: null,
  };
}

function Identifier({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10nValue>{label}</L10nValue></dt>
      <dd className="mt-1 break-all font-mono text-sm"><ReferenceText kind={label === "Bệnh nhân" ? "patient" : label === "Bác sĩ" ? "doctor" : label.startsWith("Khoa") ? "department" : label === "Hồ sơ khám" ? "record" : undefined} value={value} fallback={label} technical /></dd>
    </div>
  );
}

export function LabDetail({ testId, notice, returnTo }: LabDetailProps) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  // Keep pending reads and mutation callbacks scoped to the selected lab test.
  return <LabDetailContent key={`${testId}:${session}`} testId={testId} notice={notice} returnTo={returnTo} />;
}

function LabDetailContent({ testId, notice, returnTo }: LabDetailProps) {
  const { localize } = useLocale();
  const backHref = safeLabReturn(returnTo);
  const router = useRouter();
  const [state, setState] = useState<DetailState>({ key: "", status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const [refreshBlocked, setRefreshBlocked] = useState(false);
  const [refreshError, setRefreshError] = useState<RequestError | null>(null);
  const requestKey = `${testId}\u0000${retryToken}`;
  const loading = state.key !== requestKey;
  const retry = useCallback(() => setRetryToken((value) => value + 1), []);
  const [review, setReview] = useState<ReviewState>({ status: "loading" });
  const [reviewToken, setReviewToken] = useState(0);
  const [legacyTemplate, setLegacyTemplate] = useState(false);

  useEffect(() => {
    let active = true;
    // Only an enabled catalogue can say "this test has no template"; a disabled one says nothing.
    labApi.getTemplate(testId)
      .then((result) => { if (active) setLegacyTemplate(result.template === null); })
      .catch(() => { if (active) setLegacyTemplate(false); });
    return () => { active = false; };
  }, [testId]);

  useEffect(() => {
    let active = true;
    labApi.getReport(testId)
      .then((report) => { if (active) setReview({ status: "report", report }); })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.code === "LAB_REPORT_NOT_FOUND") setReview({ status: "none" });
        else setReview({ status: "disabled" });
      });
    return () => { active = false; };
  }, [testId, reviewToken]);

  useEffect(() => {
    let active = true;
    labApi.getById(testId)
      .then((test) => {
        if (active) {
          setRefreshError(null);
          setState({ key: requestKey, status: "success", test });
        }
      })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }
        if (cause instanceof ApiRequestError && cause.status === 404) {
          setState({ key: requestKey, status: "not-found" });
          return;
        }
        const error = getRequestError(cause);
        setRefreshError(error);
        setState(current => current.status === "success"
          ? { ...current, key: requestKey }
          : { key: requestKey, status: "error", error });
      });
    return () => {
      active = false;
    };
  }, [requestKey, router, testId]);

  if (loading && state.status !== "success") return <AsyncState kind="loading" message={localize("Đang tải chi tiết xét nghiệm…")} />;

  if (state.status === "not-found") {
    return (
      <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold"><L10n>{"Không tìm thấy xét nghiệm"}</L10n></h2>
        <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Yêu cầu có thể đã bị xóa hoặc mã truy cập không còn hợp lệ."}</L10n></p>
        <Link href={backHref} className="mt-4 inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted"><L10n>{"Quay lại danh sách"}</L10n></Link>
      </section>
    );
  }

  if (state.status === "error") {
    return <AsyncState kind="error" message={localize(state.error.message)} correlationId={state.error.correlationId} onRetry={retry} />;
  }

  if (state.status !== "success") return <AsyncState kind="empty" message={localize("Không có dữ liệu xét nghiệm.")} />;

  const test = state.test;
  const lifecycle = statusPresentation[test.status];
  const results = test.results ?? [];

  return (
    <section className="mt-6 space-y-6">
      <L10nValue>{notice === "created" ? (
        <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success"><L10n>{"Đã tạo yêu cầu xét nghiệm thành công."}</L10n></p>
      ) : null}</L10nValue>
      <PatientContextBanner label={localize("Ngữ cảnh xét nghiệm")} patientId={test.patientId}
        departmentId={test.requestingDepartmentId} date={test.requestedDate} dateLabel="Ngày yêu cầu" className="sticky top-18 z-20">
        <div className="space-y-2">
          <p className="font-semibold">{test.labType}</p>
          <div className="flex flex-wrap gap-2">
          <StatusBadge tone={lifecycle.tone}><L10nValue>{lifecycle.label}</L10nValue></StatusBadge>
          <FinancialStatus test={test} />
          </div>
        </div>
      </PatientContextBanner>

      <LabActions
        test={test}
        refreshing={loading || Boolean(refreshError)}
        onRefreshBlockedChange={setRefreshBlocked}
        onUpdated={(updated) => setState({ key: requestKey, status: "success", test: updated })}
        reviewWorkflow={review.status === "report"}
        onReviewRequired={() => setReviewToken((value) => value + 1)}
      />

      <L10nValue>{review.status === "report" || review.status === "none" ? (
        <LabReportWorkflowPanel key={reviewToken} test={test}
          initialReport={review.status === "report" ? review.report : null} onChanged={retry} />
      ) : null}</L10nValue>
      <L10nValue>{review.status === "report" || review.status === "none" ? <SpecimenPanel testId={test.testId} /> : null}</L10nValue>

      <dl className="grid gap-5 border-b border-border py-4 sm:grid-cols-2 lg:grid-cols-3">
        <div><dt className="text-sm text-muted-foreground"><L10n>{"Hình thức khám"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{test.careEpisodeType === "OUTPATIENT_VISIT" ? "Ngoại trú" : test.careEpisodeType === "ADMISSION" ? "Nội trú" : "Chưa có thông tin"}</L10nValue></dd></div>
        <div><dt className="text-sm text-muted-foreground"><L10n>{"Ngày yêu cầu"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{formatLocalDate(test.requestedDate)}</L10nValue></dd></div>
        <div><dt className="text-sm text-muted-foreground"><L10n>{"Ngày thực hiện"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{test.performedDate ? formatLocalDate(test.performedDate) : "Chưa thực hiện"}</L10nValue></dd></div>
      </dl>

      <section className="border-b border-border py-5">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-lg font-semibold"><L10n>{"Kết quả xét nghiệm"}</L10n></h2>
          <L10nValue>{legacyTemplate && results.length > 0 ? <StatusBadge tone="neutral"><L10n>{"Chưa gắn template"}</L10n></StatusBadge> : null}</L10nValue>
        </div>
        <L10nValue>{results.length === 0 ? <p className="mt-3 text-sm text-muted-foreground"><L10n>{"Chưa có chỉ số kết quả."}</L10n></p> : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border"><tr><th scope="col" className="px-3 py-2"><L10n>{"Chỉ số"}</L10n></th><th scope="col" className="px-3 py-2"><L10n>{"Giá trị"}</L10n></th><th scope="col" className="px-3 py-2"><L10n>{"Đơn vị"}</L10n></th><th scope="col" className="px-3 py-2"><L10n>{"Khoảng tham chiếu"}</L10n></th></tr></thead>
              <tbody><L10nValue>{results.map((result) => <tr key={result.resultId} className="border-b border-border last:border-0"><td className="px-3 py-3 font-medium">{result.indicator}</td><td className="px-3 py-3">{result.value}</td><td className="px-3 py-3"><L10nValue>{result.unit ?? "—"}</L10nValue></td><td className="px-3 py-3"><L10nValue>{result.referenceRange ?? "—"}</L10nValue></td></tr>)}</L10nValue></tbody>
            </table>
          </div>
        )}</L10nValue>
        <div className="mt-5 border-t border-border pt-4"><h3 className="text-sm font-semibold"><L10n>{"Kết luận"}</L10n></h3><p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground"><L10nValue>{test.conclusion ?? "Chưa có kết luận."}</L10nValue></p></div>
      </section>

      <L10nValue>{refreshError ? <div role="alert" className="rounded-lg border border-danger/30 bg-danger/5 p-4 text-sm text-danger"><p><L10nValue>{refreshError.message}</L10nValue></p><L10nValue>{refreshError.correlationId ? <details className="mt-1 text-xs"><summary className="min-h-8 cursor-pointer underline underline-offset-4"><L10n>{"Thông tin hỗ trợ"}</L10n></summary><p className="mt-1 break-all font-mono"><L10n>{"Mã hỗ trợ: "}</L10n>{refreshError.correlationId}</p></details> : null}</L10nValue><p className="mt-1 text-xs"><L10n>{"Đang hiển thị thông tin đã tải trước đó. Cập nhật lại trước khi tiếp tục xử lý."}</L10n></p></div> : null}</L10nValue>
      <div className="flex flex-wrap items-center gap-3"><Button type="button" variant="ghost" disabled={refreshBlocked || loading} onClick={retry}><L10n>{"Cập nhật trạng thái xét nghiệm"}</L10n></Button><L10nValue>{refreshBlocked ? <p className="text-xs text-muted-foreground"><L10n>{"Lưu hoặc đóng biểu mẫu trước khi cập nhật."}</L10n></p> : null}</L10nValue><L10nValue>{loading ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Đang cập nhật…"}</L10n></p> : null}</L10nValue></div>

      <details className="border-t border-border py-3">
        <summary className="min-h-11 cursor-pointer py-2 text-sm font-medium"><L10n>{"Thông tin bàn giao thu ngân và hỗ trợ"}</L10n></summary>
        <dl className="mt-3 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        <Identifier label={localize("Mã xét nghiệm")} value={test.testId} />
        <Identifier label={localize("Hồ sơ khám")} value={test.recordId} />
        <Identifier label={localize("Khoa yêu cầu")} value={test.requestingDepartmentId} />
        <Identifier label={localize("Nguồn chỉ định")} value={test.sourceOrderId} />
        <Identifier label={localize("Đợt chăm sóc")} value={test.careEpisodeId} />
        <Identifier label={localize("Xác nhận tài chính")} value={test.clearanceId} />
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Mã giá"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{test.priceCode ?? "—"}</L10nValue></dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Phiên bản dữ liệu"}</L10n></dt><dd className="mt-1 text-sm">{test.careContractVersion} / {test.resultVersion}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Ngày tạo"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{formatInstant(test.createdAt)}</L10nValue></dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Cập nhật"}</L10n></dt><dd className="mt-1 text-sm"><L10nValue>{formatInstant(test.updatedAt)}</L10nValue></dd></div>
        </dl>
        <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Thu ngân mở “Thu phí xét nghiệm &amp; thuốc”, tìm bệnh nhân và cập nhật hàng chờ. Các mã dưới đây dùng khi cần hỗ trợ đối soát; nhấp vào ô để chọn và sao chép."}</L10n></p>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <L10nValue>{[["test", "Mã xét nghiệm bàn giao", test.testId], ["patient", "Mã bệnh nhân bàn giao", test.patientId], ["episode", "Mã đợt chăm sóc bàn giao", test.careEpisodeId]].map(([key, label, value]) => <label key={key} htmlFor={`lab-handoff-${key}`} className="space-y-1 text-sm"><L10nValue>{label}</L10nValue>
            <L10nValue>{value ? <input id={`lab-handoff-${key}`} readOnly value={value} onFocus={event => event.currentTarget.select()} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 font-mono text-sm" /> : <span className="block text-warning"><L10n>{"Chưa có mã đợt chăm sóc. Liên hệ bộ phận chỉ định để đối soát."}</L10n></span>}</L10nValue>
          </label>)}</L10nValue>
        </div>
      </details>


      <L10nValue>{test.emergencyOverrideId ? <section className="rounded-xl border border-warning/40 bg-warning/10 p-4 text-sm"><p className="font-medium"><L10n>{"Có phê duyệt ngoại lệ khẩn cấp"}</L10n></p><p className="mt-1 break-all font-mono text-xs"><ReferenceText value={test.emergencyOverrideId} fallback="Phê duyệt ngoại lệ" /></p></section> : null}</L10nValue>
      <Link href={backHref} className="inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted"><L10n>{"Quay lại danh sách"}</L10n></Link>
    </section>
  );
}
