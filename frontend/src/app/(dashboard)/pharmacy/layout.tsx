import type { ReactNode } from "react";
import { PharmacyNav } from "@/features/pharmacy/components/PharmacyNav";

interface PharmacyLayoutProps {
  children: ReactNode;
}

/**
 * Khung dùng chung cho các route nằm trong /pharmacy.
 *
 * Giữ file này là Server Component:
 * - Chỉ ghép menu con với nội dung của route hiện tại.
 * - Không đọc localStorage.
 * - Không gọi API hoặc xử lý nghiệp vụ.
 *
 * PharmacyNav là Client Component riêng vì cần đọc role
 * và xác định mục menu đang được chọn.
 */
export default function PharmacyLayout({
  children,
}: Readonly<PharmacyLayoutProps>) {
  return (
    <>
      {/* DashboardHeader đã được render ở dashboard layout bên ngoài. */}
      {/* Layout này chỉ bổ sung thanh điều hướng riêng của Pharmacy. */}
      <div className="border-b border-border bg-surface">
        <div className="mx-auto max-w-6xl px-6 py-3">
          <PharmacyNav />
        </div>
      </div>

      {/*
        Nội dung của page hiện tại được render tại đây.

        Không đặt PageShell trong layout này vì từng page
        cần có tiêu đề, mô tả và PageShell riêng.
      */}
      {children}
    </>
  );
}