"use client";

import Link from "next/link";
import { useEffect, useRef, useSyncExternalStore, useState } from "react";
import { useRouter } from "next/navigation";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/session";
import type { Role } from "@/lib/roles";
import { pharmacyApi } from "../../api";
import { getPharmacyCapabilities } from "../../permissions";
import type { DrugDTO } from "../../types";
import {
  formatDate,
  formatDateTime,
  formatVnd,
  getDrugExpiryState,
} from "../../utils";
import { AdjustStockForm } from "./AdjustStockForm";

interface DrugDetailProps {
  drugId: string;
}

type DetailRequestState =
  | { key: string; status: "idle" }
  | { key: string; status: "success"; drug: DrugDTO }
  | { key: string; status: "not-found" }
  | { key: string; status: "error"; message: string };

function subscribeToRoleChanges(onStoreChange: () => void) {
  window.addEventListener("storage", onStoreChange);
  window.addEventListener("focus", onStoreChange);

  return () => {
    window.removeEventListener("storage", onStoreChange);
    window.removeEventListener("focus", onStoreChange);
  };
}

function getServerRole(): Role | null {
  return null;
}

function detailErrorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) return "Bạn không có quyền xem thuốc này.";
    if (cause.correlationId) {
      return `${cause.message} (Mã tra cứu: ${cause.correlationId})`;
    }
    return cause.message;
  }
  return cause instanceof Error ? cause.message : "Không thể tải thông tin thuốc.";
}

function expiryPresentation(expiryDate: string) {
  switch (getDrugExpiryState(expiryDate)) {
    case "EXPIRED":
      return { label: "Hết hạn", tone: "danger" as const };
    case "EXPIRING_SOON":
      return { label: "Sắp hết hạn", tone: "warning" as const };
    default:
      return null;
  }
}

function DetailLoading() {
  return (
    <section className="mt-6 space-y-4" aria-busy="true" aria-label="Đang tải thông tin thuốc">
      <div className="h-28 animate-pulse rounded-xl bg-surface-muted" />
      <div className="h-52 animate-pulse rounded-xl bg-surface-muted" />
    </section>
  );
}

function DetailError({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p>{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-3 rounded-lg border border-danger/40 px-3 py-2 font-medium hover:bg-danger/10"
      >
        Thử lại
      </button>
    </div>
  );
}

/** Fetches one drug and composes permission-gated catalogue actions. */
export function DrugDetail({ drugId }: DrugDetailProps) {
  const router = useRouter();
  const role = useSyncExternalStore(
    subscribeToRoleChanges,
    getRole,
    getServerRole,
  );
  const [requestState, setRequestState] = useState<DetailRequestState>({
    key: "",
    status: "idle",
  });
  const [retryToken, setRetryToken] = useState(0);
  const requestSequence = useRef(0);
  const requestKey = `${drugId}\u0000${retryToken}`;
  const loading = requestState.key !== requestKey;
  const drug =
    requestState.status === "success" && requestState.drug.drugId === drugId
      ? requestState.drug
      : null;
  const notFound =
    requestState.key === requestKey && requestState.status === "not-found";
  const error =
    requestState.key === requestKey && requestState.status === "error"
      ? requestState.message
      : null;

  useEffect(() => {
    let active = true;
    const requestId = ++requestSequence.current;

    pharmacyApi
      .getDrug(drugId)
      .then((nextDrug) => {
        if (!active || requestId !== requestSequence.current) return;
        setRequestState({ key: requestKey, status: "success", drug: nextDrug });
      })
      .catch((cause: unknown) => {
        if (!active || requestId !== requestSequence.current) return;

        if (cause instanceof ApiRequestError && cause.status === 401) {
          setRequestState({
            key: requestKey,
            status: "error",
            message: "Phiên đăng nhập đã hết hạn. Đang chuyển đến trang đăng nhập.",
          });
          router.replace("/login");
          return;
        }

        if (
          cause instanceof ApiRequestError &&
          (cause.status === 404 || cause.code === "DRUG_NOT_FOUND")
        ) {
          setRequestState({ key: requestKey, status: "not-found" });
          return;
        }

        setRequestState({
          key: requestKey,
          status: "error",
          message: detailErrorMessage(cause),
        });
      });

    return () => {
      active = false;
    };
  }, [drugId, requestKey, router]);

  if (loading && !drug && !notFound && !error) return <DetailLoading />;

  if (notFound) {
    return (
      <section className="mt-6 rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Không tìm thấy thuốc</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Không có thuốc nào với mã <code className="break-all">{drugId}</code>.
        </p>
        <Link
          href="/pharmacy/drugs"
          className="mt-4 inline-flex rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90"
        >
          Quay lại kho thuốc
        </Link>
      </section>
    );
  }

  if (error) {
    return (
      <DetailError
        message={error}
        onRetry={() => setRetryToken((value) => value + 1)}
      />
    );
  }

  if (!drug) return null;

  const capabilities = getPharmacyCapabilities(role);
  const expiry = expiryPresentation(drug.expiryDate);
  const lowStock = drug.stockQuantity <= drug.lowStockThreshold;

  return (
    <section className="mt-6 space-y-6">
      {loading && (
        <p role="status" className="rounded-lg border border-info/40 bg-info/10 p-3 text-sm text-info">
          Đang xác nhận snapshot thuốc mới nhất…
        </p>
      )}
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="font-mono text-xs text-muted-foreground">{drug.drugId}</p>
          <h2 className="mt-1 text-xl font-semibold">{drug.drugName}</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {drug.activeIngredient ?? "—"}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {capabilities.canCreateDrug && (
            <Link
              href="/pharmacy/drugs/new"
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium hover:bg-surface-muted"
            >
              Tạo thuốc
            </Link>
          )}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="rounded-xl border border-border bg-surface p-6">
          <h3 className="text-base font-semibold">Thông tin thuốc</h3>
          <dl className="mt-4 grid gap-4 sm:grid-cols-2">
            <div><dt className="text-xs text-muted-foreground">Hoạt chất</dt><dd className="mt-1">{drug.activeIngredient ?? "—"}</dd></div>
            <div><dt className="text-xs text-muted-foreground">Đơn vị</dt><dd className="mt-1">{drug.unit || "—"}</dd></div>
            <div><dt className="text-xs text-muted-foreground">Nhà sản xuất</dt><dd className="mt-1">{drug.manufacturer ?? "—"}</dd></div>
            <div><dt className="text-xs text-muted-foreground">Hạn dùng</dt><dd className="mt-1 flex flex-wrap items-center gap-2">{formatDate(drug.expiryDate)}{expiry && <StatusBadge tone={expiry.tone}>{expiry.label}</StatusBadge>}</dd></div>
          </dl>
        </section>

        <section className="rounded-xl border border-border bg-surface p-6">
          <h3 className="text-base font-semibold">Giá và tồn kho</h3>
          <dl className="mt-4 grid gap-4 sm:grid-cols-2">
            <div><dt className="text-xs text-muted-foreground">Đơn giá</dt><dd className="mt-1 font-semibold">{formatVnd(drug.price)}</dd></div>
            <div><dt className="text-xs text-muted-foreground">Tồn hiện tại</dt><dd className="mt-1 flex flex-wrap items-center gap-2">{drug.stockQuantity}{lowStock && <StatusBadge tone="warning">Tồn thấp</StatusBadge>}</dd></div>
            <div><dt className="text-xs text-muted-foreground">Ngưỡng cảnh báo</dt><dd className="mt-1">{drug.lowStockThreshold}</dd></div>
          </dl>
          {capabilities.canAdjustStock && (
            <AdjustStockForm
              drug={drug}
              onSuccess={(updatedDrug) => {
                setRequestState({ key: requestKey, status: "success", drug: updatedDrug });
              }}
              onConflict={() => setRetryToken((value) => value + 1)}
            />
          )}
        </section>
      </div>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h3 className="text-base font-semibold">Lịch sử bản ghi</h3>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <div><dt className="text-xs text-muted-foreground">Tạo lúc</dt><dd className="mt-1">{formatDateTime(drug.createdAt)}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Cập nhật lúc</dt><dd className="mt-1">{formatDateTime(drug.updatedAt)}</dd></div>
        </dl>
      </section>
    </section>
  );
}
