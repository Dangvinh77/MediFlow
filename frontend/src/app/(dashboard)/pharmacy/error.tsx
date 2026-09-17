"use client";

import Link from "next/link";
import { PageShell } from "@/components/layout/PageShell";

interface PharmacyErrorProps {
  /**
   * Lỗi render ngoài dự kiến được chuyển đến error boundary.
   * digest là mã tham chiếu lỗi khi có, không phải correlationId của API.
   */
  error: Error & { digest?: string };

  /**
   * Yêu cầu thử render lại phần route đang bị lỗi.
   * Không thay thế thao tác retry API tại component nghiệp vụ.
   */
  reset: () => void;
}

/**
 * Giao diện dự phòng khi nhánh route Pharmacy gặp lỗi render.
 *
 * Phải là Client Component vì có nút gọi reset().
 *
 * Không dùng boundary này để xử lý mọi lỗi nghiệp vụ:
 * - Lỗi validation cần hiển thị gần field.
 * - Lỗi không tìm thấy tài nguyên cần có state riêng.
 * - Lỗi quyền hoặc lifecycle cần được xử lý tại component tương ứng.
 */
export default function PharmacyError({
  error,
  reset,
}: PharmacyErrorProps) {
  return (
    <PageShell title="Không thể hiển thị trang Dược">
      <section
        role="alert"
        className="mt-6 rounded-xl border border-danger/30 bg-danger/10 p-5"
      >
        {/*
          Không hiển thị trực tiếp error.message hoặc stack trace
          để tránh đưa chi tiết lỗi nội bộ ra giao diện.
        */}
        <p className="text-danger">
          Đã xảy ra lỗi khi hiển thị trang. Bạn có thể thử lại
          hoặc quay về kho thuốc.
        </p>

        {/* Chỉ hiển thị mã tham chiếu khi error có digest. */}
        {error.digest && (
          <p className="mt-2 break-all text-sm text-muted-foreground">
            Mã lỗi: <code>{error.digest}</code>
          </p>
        )}

        <div className="mt-4 flex flex-wrap gap-3">
          <button
            type="button"
            onClick={reset}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Thử lại
          </button>

          <Link
            href="/pharmacy/drugs"
            className="rounded-lg border border-border bg-surface px-4 py-2 text-sm font-medium text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Về kho thuốc
          </Link>
        </div>
      </section>
    </PageShell>
  );
}