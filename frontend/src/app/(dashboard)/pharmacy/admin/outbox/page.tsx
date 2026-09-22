import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
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
      <RoleGate allowed={["ADMIN"]}>
        {/*
          Người truy cập URL trực tiếp vẫn phải qua backend authorization.
          PH-FE-10 sẽ thay nội dung tĩnh bằng form replay known eventId.
        */}
        <p className="mt-6 text-sm text-muted-foreground">
          Biểu mẫu replay sẽ được triển khai ở PH-FE-10.
          Không có bảng danh sách outbox trong phạm vi hiện tại.
        </p>
      </RoleGate>
    </PageShell>
  );
}
