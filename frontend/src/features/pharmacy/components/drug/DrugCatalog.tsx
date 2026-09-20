"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Pagination } from "@/components/ui/Pagination";
import { ApiRequestError } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { Role } from "@/lib/roles";
import { getRole } from "@/lib/session";
import { pharmacyApi } from "../../api";
import { getPharmacyCapabilities } from "../../permissions";
import type { DrugDTO } from "../../types";
import {
  buildDrugCatalogQuery,
  DRUG_CATALOG_PAGE_SIZES,
  parseDrugCatalogQuery,
  type DrugCatalogQuery,
} from "../../utils";
import { DrugTable } from "./DrugTable";

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

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) return "Bạn không có quyền xem danh mục thuốc.";
    if (cause.correlationId) {
      return `${cause.message} (Mã tra cứu: ${cause.correlationId})`;
    }
    return cause.message;
  }

  return cause instanceof Error
    ? cause.message
    : "Không thể tải danh mục thuốc.";
}

function CatalogFallback() {
  return (
    <section className="mt-6" aria-busy="true" aria-label="Đang tải danh mục thuốc">
      <div className="h-11 animate-pulse rounded-lg bg-surface-muted" />
      <div className="mt-6 h-64 animate-pulse rounded-xl bg-surface-muted" />
    </section>
  );
}

export { CatalogFallback };

/** Owns URL-driven query state and the single catalogue read request. */
export function DrugCatalog() {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const role = useSyncExternalStore(
    subscribeToRoleChanges,
    getRole,
    getServerRole,
  );
  const rawQuery = searchParams.toString();
  const query = useMemo(
    () => parseDrugCatalogQuery(new URLSearchParams(rawQuery)),
    [rawQuery],
  );
  const { keyword, page, size } = query;
  const [result, setResult] = useState<PageResult<DrugDTO> | null>(null);
  const [settledRequestKey, setSettledRequestKey] = useState<string | null>(null);
  const [requestError, setRequestError] = useState<{
    key: string;
    message: string;
  } | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestSequence = useRef(0);

  const queryKey = `${keyword}\u0000${page}\u0000${size}`;
  const requestKey = `${queryKey}\u0000${retryToken}`;
  const loading = settledRequestKey !== requestKey;
  const error =
    settledRequestKey === requestKey && requestError?.key === requestKey
      ? requestError.message
      : null;
  const canCreateDrug = getPharmacyCapabilities(role).canCreateDrug;

  const updateQuery = useCallback(
    (changes: Partial<DrugCatalogQuery>) => {
      const nextQuery: DrugCatalogQuery = {
        page: changes.page ?? page,
        size: changes.size ?? size,
        keyword: (changes.keyword ?? keyword).trim(),
      };

      router.replace(
        `${pathname}?${buildDrugCatalogQuery(nextQuery)}`,
        { scroll: false },
      );
    },
    [keyword, page, pathname, router, size],
  );

  useEffect(() => {
    let active = true;
    const requestId = ++requestSequence.current;

    pharmacyApi
      .searchDrugs({ keyword, page, size })
      .then((nextResult) => {
        if (!active || requestId !== requestSequence.current) return;

        // If records were removed after a bookmarked URL was created, move to
        // the last available page instead of rendering a misleading empty page.
        if (
          nextResult.totalPages > 0 &&
          nextResult.number >= nextResult.totalPages
        ) {
          updateQuery({ page: nextResult.totalPages - 1 });
          return;
        }

        setResult(nextResult);
      })
      .catch((cause: unknown) => {
        if (!active || requestId !== requestSequence.current) return;

        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }

        setRequestError({ key: requestKey, message: errorMessage(cause) });
      })
      .finally(() => {
        if (active && requestId === requestSequence.current) {
          setSettledRequestKey(requestKey);
        }
      });

    return () => {
      active = false;
    };
  }, [keyword, page, requestKey, router, size, updateQuery]);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const nextKeyword = String(formData.get("keyword") ?? "");
    updateQuery({ keyword: nextKeyword, page: 0 });
  }

  function selectSize(nextSize: string) {
    const size = Number.parseInt(nextSize, 10);
    if (!DRUG_CATALOG_PAGE_SIZES.includes(size as (typeof DRUG_CATALOG_PAGE_SIZES)[number])) {
      return;
    }
    updateQuery({ size, page: 0 });
  }

  const hasData = Boolean(result && result.content.length > 0);

  return (
    <section className="mt-6" aria-busy={loading}>
      {canCreateDrug && (
        <div className="mb-4 flex justify-end">
          <Link
            href="/pharmacy/drugs/new"
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90"
          >
            Tạo thuốc
          </Link>
        </div>
      )}
      <form onSubmit={submitSearch} className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="drug-keyword" className="mb-1 block text-sm font-medium">
            Tìm theo tên thuốc
          </label>
          <input
            key={keyword}
            id="drug-keyword"
            name="keyword"
            defaultValue={keyword}
            placeholder="Ví dụ: Paracetamol"
            className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground"
          />
        </div>
        <div>
          <label htmlFor="drug-page-size" className="mb-1 block text-sm font-medium">
            Số dòng
          </label>
          <select
            id="drug-page-size"
            value={query.size}
            onChange={(event) => selectSize(event.target.value)}
            className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground sm:w-auto"
          >
            {DRUG_CATALOG_PAGE_SIZES.map((size) => (
              <option key={size} value={size}>{size}</option>
            ))}
          </select>
        </div>
        <button
          type="submit"
          disabled={loading}
          className="rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Tìm kiếm
        </button>
      </form>

      {loading && !result && <CatalogFallback />}

      {error && (
        <div role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          <p>{error}</p>
          <button
            type="button"
            onClick={() => setRetryToken((value) => value + 1)}
            className="mt-3 rounded-lg border border-danger/40 px-3 py-2 font-medium hover:bg-danger/10"
          >
            Thử lại
          </button>
        </div>
      )}

      {!loading && !error && result && result.content.length === 0 && (
        <p className="mt-6 rounded-lg border border-border bg-surface p-6 text-sm text-muted-foreground">
          Không tìm thấy thuốc phù hợp với bộ lọc hiện tại.
        </p>
      )}

      {!error && hasData && result && (
        <>
          <div className="mt-6 flex items-center justify-between gap-3 text-sm text-muted-foreground">
            <span>
              {result.totalElements.toLocaleString("vi-VN")} thuốc
              {query.keyword ? ` phù hợp với “${query.keyword}”` : " trong danh mục"}
            </span>
            {loading && <span role="status">Đang cập nhật…</span>}
          </div>
          <DrugTable drugs={result.content} />
          <Pagination
            page={result.number}
            totalPages={result.totalPages}
            loading={loading}
            onPageChange={(page) => updateQuery({ page })}
          />
        </>
      )}
    </section>
  );
}
