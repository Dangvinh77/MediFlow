import type { Metadata } from "next";
import { PageShell } from "@/components/layout/PageShell";

export const metadata: Metadata = {
  title: "Outbox Pharmacy | MediFlow",
};

/**
 * Route công cụ replay một outbox event theo eventId đã biết.
 *
 * PH-FE-01 chỉ tạo khung tĩnh, chưa có dữ liệu hoặc nút replay.
 * Việc ẩn menu Outbox với non-admin không tự bảo vệ URL này.
 *
 * TODO(PH-FE-10):
 * - Render OutboxReplayForm.
 * - Chỉ cho ADMIN thấy form/action ở mức UX.
 * - Gửi request qua pharmacyApi và xử lý backend 403.
 * - Không tạo bảng danh sách outbox hoặc tự invent API list.
 */
export default function OutboxReplayPage() {
  return (
    <PageShell
      title="Outbox Pharmacy"
      description="Công cụ dành cho ADMIN để replay event theo mã đã biết."
    >
      {/*
        Người truy cập URL trực tiếp có thể thấy khung tĩnh này.
        Không xem việc render được khung trang là có quyền replay.
      */}
      <p className="mt-6 text-sm text-muted-foreground">
        Biểu mẫu replay sẽ được triển khai ở PH-FE-10.
        Không có bảng danh sách outbox trong phạm vi hiện tại.
      </p>
    </PageShell>
  );
}