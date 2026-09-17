interface PaginationProps {
  page: number;
  totalPages: number;
  loading: boolean;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, loading, onPageChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <nav className="mt-4 flex items-center justify-end gap-3" aria-label="Phân trang">
      <button
        type="button"
        disabled={loading || page === 0}
        onClick={() => onPageChange(page - 1)}
        className="rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium text-foreground transition-colors hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50"
      >
        Trang trước
      </button>
      <span className="text-sm text-muted-foreground">
        Trang {page + 1}/{totalPages}
      </span>
      <button
        type="button"
        disabled={loading || page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
        className="rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium text-foreground transition-colors hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50"
      >
        Trang sau
      </button>
    </nav>
  );
}
