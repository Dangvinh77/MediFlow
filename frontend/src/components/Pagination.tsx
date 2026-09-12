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
        className="rounded-lg border border-zinc-300 px-3 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700"
      >
        Trang trước
      </button>
      <span className="text-sm text-zinc-600 dark:text-zinc-400">
        Trang {page + 1}/{totalPages}
      </span>
      <button
        type="button"
        disabled={loading || page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
        className="rounded-lg border border-zinc-300 px-3 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700"
      >
        Trang sau
      </button>
    </nav>
  );
}
