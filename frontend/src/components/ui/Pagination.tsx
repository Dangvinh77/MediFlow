interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements?: number;
  loading: boolean;
  onPageChange: (page: number) => void;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  loading,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1 && totalElements === undefined) return null;

  const previousDisabled = loading || page === 0;
  const nextDisabled = loading || page + 1 >= totalPages;

  return (
    <nav
      className="mt-4 flex flex-wrap items-center justify-end gap-3"
      aria-label="Phân trang"
    >
      <button
        type="button"
        disabled={previousDisabled}
        aria-disabled={previousDisabled}
        onClick={() => onPageChange(page - 1)}
        className="min-h-12 rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
      >
        Trang trước
      </button>
      <span className="text-sm text-muted-foreground">
        Trang {page + 1}/{Math.max(totalPages, 1)}
        {totalElements !== undefined ? (
          <> · {totalElements} kết quả</>
        ) : null}
      </span>
      <button
        type="button"
        disabled={nextDisabled}
        aria-disabled={nextDisabled}
        onClick={() => onPageChange(page + 1)}
        className="min-h-12 rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
      >
        Trang sau
      </button>
    </nav>
  );
}
