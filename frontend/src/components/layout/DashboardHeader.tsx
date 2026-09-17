"use client";

import Link from "next/link";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import type { Role } from "@/lib/roles";

/**
 * Các mục điều hướng đang có của dashboard.
 *
 * Giữ nguyên danh sách này trong PH-FE-01.
 * Link Dược được render riêng bên dưới vì có điều kiện hiển thị theo role.
 */
const navigation = [
  { href: "/patients", label: "Bệnh nhân" },
  { href: "/appointments", label: "Lịch hẹn" },
  { href: "/records", label: "Hồ sơ" },
  { href: "/lab", label: "Xét nghiệm" },
];

interface DashboardHeaderProps {
  // Role được dashboard layout truyền xuống.
  // Header không tự đọc hoặc quản lý session.
  role: Role | null;

  // Việc xóa session và chuyển về login do component cha xử lý.
  onLogout: () => void;
}

/**
 * Header dùng chung của dashboard.
 *
 * Trách nhiệm:
 * - Hiển thị các đường dẫn cấp ứng dụng.
 * - Hiển thị role hiện tại.
 * - Giữ công tắc theme và nút đăng xuất.
 *
 * Không import component/API của Pharmacy vào shared header.
 * Menu con và capability chi tiết của Pharmacy do PharmacyNav quản lý.
 */
export function DashboardHeader({
  role,
  onLogout,
}: DashboardHeaderProps) {
  /**
   * Chỉ quyết định có hiển thị link vào phân hệ Dược hay không.
   *
   * Đây không phải kiểm tra quyền gọi API.
   * Những action bên trong Pharmacy vẫn cần permission UX riêng
   * và backend vẫn là nơi thực thi authorization.
   */
  const showPharmacy =
    role === "ADMIN" ||
    role === "DOCTOR" ||
    role === "PHARMACIST";

  return (
    <header className="border-b border-border bg-surface">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-4">
        {/* Liên kết thương hiệu đưa người dùng về trang gốc. */}
        <Link href="/" className="text-lg font-bold">
          MediFlow
        </Link>

        <nav
          aria-label="Điều hướng chính"
          className="flex flex-wrap items-center gap-4 text-sm"
        >
          {/* Giữ nguyên các mục điều hướng hiện có. */}
          {navigation.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="hover:text-blue-600"
            >
              {item.label}
            </Link>
          ))}

          {/*
            Link đi vào route /pharmacy.
            Route đó sẽ redirect sang /pharmacy/drugs.
          */}
          {showPharmacy && (
            <Link
              href="/pharmacy"
              className="hover:text-primary"
            >
              Dược
            </Link>
          )}
        </nav>

        <div className="flex items-center gap-3 text-sm">
          {role && (
            <span className="text-muted-foreground">
              {role}
            </span>
          )}

          {/* Tái sử dụng hệ thống theme đang có của ứng dụng. */}
          <ThemeToggle />

          {/*
            Header chỉ gọi callback.
            Không tự xóa token hoặc thực hiện điều hướng tại đây.
          */}
          <button
            type="button"
            onClick={onLogout}
            className="rounded-lg border border-border px-3 py-1.5"
          >
            Đăng xuất
          </button>
        </div>
      </div>
    </header>
  );
}