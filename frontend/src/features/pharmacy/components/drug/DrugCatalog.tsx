"use client";

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { controlClassName } from "@/components/ui/Field";
import { Pagination } from "@/components/ui/Pagination";
import { ApiRequestError } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import { pharmacyApi } from "../../api";
import type { DrugDTO } from "../../types";
import {
  buildDrugCatalogQuery,
  DRUG_CATALOG_PAGE_SIZES,
  DRUG_CATALOG_FILTERS,
  type DrugCatalogFilter,
  parseDrugCatalogQuery,
  type DrugCatalogQuery,
} from "../../utils";
import { DrugTable } from "./DrugTable";
import { PharmacySessionBoundary, usePharmacyRequestScope } from "../PharmacySessionBoundary";

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) {
      const correlation = cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
      return `Bạn không có quyền xem danh mục thuốc.${correlation}`;
    }
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
  return <PharmacySessionBoundary capability="canReadDrugs"><DrugCatalogContent /></PharmacySessionBoundary>;
}

function DrugCatalogContent() {
  const isCurrent = usePharmacyRequestScope("canReadDrugs");
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const rawQuery = searchParams.toString();
  const query = useMemo(
    () => parseDrugCatalogQuery(new URLSearchParams(rawQuery)),
    [rawQuery],
  );
  const { keyword, page, size, filter } = query;
  const [loadedResult, setResult] = useState<{ key: string; data: PageResult<DrugDTO> } | null>(null);
  const [settledRequestKey, setSettledRequestKey] = useState<string | null>(null);
  const [requestError, setRequestError] = useState<{
    key: string;
    message: string;
    forbidden: boolean;
  } | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestSequence = useRef(0);

  const queryKey = `${keyword}\u0000${page}\u0000${size}\u0000${filter ?? "ALL"}`;
  const requestKey = `${queryKey}\u0000${retryToken}`;
  const result = loadedResult?.key === requestKey ? loadedResult.data : null;
  const loading = settledRequestKey !== requestKey;
  const error =
    settledRequestKey === requestKey && requestError?.key === requestKey
      ? requestError.message
      : null;

  const updateQuery = useCallback(
    (changes: Partial<DrugCatalogQuery>) => {
      const nextQuery: DrugCatalogQuery = {
        page: changes.page ?? page,
        size: changes.size ?? size,
        keyword: (changes.keyword ?? keyword).trim(),
        ...((changes.filter ?? filter) ? { filter: changes.filter ?? filter } : {}),
      };

      router.replace(
        `${pathname}?${buildDrugCatalogQuery(nextQuery)}`,
        { scroll: false },
      );
    },
    [keyword, page, pathname, router, size, filter],
  );

  useEffect(() => {
    if (!isCurrent()) return;
    let active = true;
    const requestId = ++requestSequence.current;

    pharmacyApi
      .searchDrugs({ keyword, page, size, ...(filter ? { filter } : {}) })
      .then((nextResult) => {
        if (!active || !isCurrent() || requestId !== requestSequence.current) return;

        // If records were removed after a bookmarked URL was created, move to
        // the last available page instead of rendering a misleading empty page.
        if (
          nextResult.totalPages > 0 &&
          nextResult.number >= nextResult.totalPages
        ) {
          updateQuery({ page: nextResult.totalPages - 1 });
          return;
        }

        setResult({ key: requestKey, data: nextResult });
      })
      .catch((cause: unknown) => {
        if (!active || !isCurrent() || requestId !== requestSequence.current) return;

        if (cause instanceof ApiRequestError && cause.status === 401) {
          setResult(null);
          router.replace("/login");
          return;
        }

        setRequestError({ key: requestKey, message: errorMessage(cause), forbidden: cause instanceof ApiRequestError && cause.status === 403 });
      })
      .finally(() => {
        if (active && isCurrent() && requestId === requestSequence.current) {
          setSettledRequestKey(requestKey);
        }
      });

    return () => {
      active = false;
    };
  }, [isCurrent, keyword, page, requestKey, router, size, updateQuery, filter]);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (requestError?.key === requestKey && requestError.forbidden) return;
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

  const hasData = Boolean(!loading && !error && result && result.content.length > 0);

  return (
    <section className="mt-6 rounded-xl border border-border bg-surface p-4 sm:p-5" aria-busy={loading} aria-label="Danh mục thuốc">
      <div className="flex flex-col justify-between gap-4 xl:flex-row xl:items-end">
        <div>
          <h2 className="text-base font-semibold" aria-live="polite">
            {!loading && !error && result ? result.totalElements.toLocaleString("vi-VN") + " thuốc" : "Danh mục thuốc"}
          </h2>
          <p className="mt-1 text-xs text-muted-foreground">
            {keyword ? "Tìm theo tên: “" + keyword + "”" : "Giá và tồn kho hiện tại"}
          </p>
        </div>
        <form onSubmit={submitSearch} className="flex flex-col gap-3 sm:flex-row sm:items-center xl:w-3/5">
          <div className="min-w-0 flex-1">
            <label htmlFor="drug-keyword" className="sr-only">Tìm theo tên thuốc</label>
            <input maxLength={200} key={keyword} id="drug-keyword" name="keyword" type="search" defaultValue={keyword} placeholder="Tên thuốc hoặc hoạt chất" className={controlClassName} />
          </div>
          <div>
            <label htmlFor="drug-filter" className="sr-only">Tình trạng thuốc</label>
            <select id="drug-filter" value={filter ?? "ALL"} className={controlClassName}
              disabled={requestError?.key === requestKey && requestError.forbidden}
              onChange={event => updateQuery({ filter: event.target.value as DrugCatalogFilter, page: 0 })}>
              {Object.entries(DRUG_CATALOG_FILTERS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </div>
          <Button type="submit" variant="primary" disabled={loading || (requestError?.key === requestKey && requestError.forbidden)}>Tìm kiếm</Button>
        </form>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
        <p>Tìm được cả tên thuốc và hoạt chất. Tồn kho là số lượng hiện có, có thể gồm thuốc đang giữ cho đơn.</p>
        {(keyword || filter) && <Button type="button" disabled={requestError?.forbidden}
          onClick={() => updateQuery({ keyword: "", filter: "ALL", page: 0 })}>Xóa bộ lọc</Button>}
      </div>
      {loading && <CatalogFallback />}
      {error && (
        <div role="alert" className="mt-5 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          <p>{error}</p>
          {!requestError?.forbidden && <Button type="button" className="mt-3" onClick={() => setRetryToken((value) => value + 1)}>Thử lại</Button>}
        </div>
      )}
      {!loading && !error && result && result.content.length === 0 && (
        <p role="status" className="py-8 text-sm text-muted-foreground">
          {keyword.trim() || filter ? "Không tìm thấy thuốc phù hợp. Thử đổi tên, hoạt chất hoặc xóa bộ lọc." : "Chưa có thuốc trong danh mục. Người quản lý nhà thuốc cần bổ sung danh mục để bắt đầu."}
        </p>
      )}
      {hasData && result && (
        <>
          <DrugTable drugs={result.content} />
          <div className="mt-4 flex flex-col gap-3 border-t border-border pt-4 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-xs text-muted-foreground">
                Hiển thị {(result.number * result.size + 1).toLocaleString("vi-VN")}–{(result.number * result.size + result.content.length).toLocaleString("vi-VN")} trong {result.totalElements.toLocaleString("vi-VN")} thuốc
              </p>
              <label htmlFor="drug-page-size" className="sr-only">Số dòng</label>
              <select id="drug-page-size" value={query.size} onChange={(event) => selectSize(event.target.value)} className={`${controlClassName} w-auto`}>
                {DRUG_CATALOG_PAGE_SIZES.map((size) => <option key={size} value={size}>{size} / trang</option>)}
              </select>
            </div>
            <Pagination page={result.number} totalPages={result.totalPages} totalElements={result.totalElements} loading={loading} onPageChange={(page) => updateQuery({ page })} />
          </div>
        </>
      )}
    </section>
  );
}
