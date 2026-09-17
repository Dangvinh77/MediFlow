"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSyncExternalStore } from "react";
import type { Role } from "@/lib/roles";
import { getRole } from "@/lib/session";
import {
  getPharmacyCapabilities,
  type PharmacyCapabilities,
} from "../permissions";

/**
 * Cấu hình một mục trong menu con của Pharmacy.
 *
 * Mỗi mục tham chiếu một capability trong permissions.ts,
 * thay vì khai báo lại danh sách role được phép ở từng mục.
 */
interface PharmacyNavItem {
  href: string;
  label: string;
  capability: keyof PharmacyCapabilities;
}

/**
 * Danh sách route thuộc phân hệ Dược.
 *
 * Đây chỉ là cấu hình điều hướng, không thực hiện gọi API
 * và không thay thế việc kiểm tra quyền tại backend.
 */
const navigation: readonly PharmacyNavItem[] = [
  {
    href: "/pharmacy/drugs",
    label: "Kho thuốc",
    capability: "canReadDrugs",
  },
  {
    href: "/pharmacy/prescriptions/new",
    label: "Kê đơn",
    capability: "canCreatePrescription",
  },
  {
    href: "/pharmacy/prescriptions",
    label: "Tra đơn",
    capability: "canReadPrescription",
  },
  {
    href: "/pharmacy/admin/outbox",
    label: "Outbox",
    capability: "canReplayOutbox",
  },
];

/**
 * Yêu cầu React đọc lại role khi:
 * - Trình duyệt nhận sự kiện storage.
 * - Cửa sổ/tab nhận lại focus.
 *
 * Hàm trả về dùng để gỡ listener khi component không còn sử dụng.
 *
 * Lưu ý: đoạn này không bổ sung cơ chế phát sự kiện session
 * trong cùng tab. Luồng login/logout hiện tại vẫn giữ nguyên.
 */
function subscribeToRoleChanges(onStoreChange: () => void) {
  window.addEventListener("storage", onStoreChange);
  window.addEventListener("focus", onStoreChange);

  return () => {
    window.removeEventListener("storage", onStoreChange);
    window.removeEventListener("focus", onStoreChange);
  };
}

/**
 * Server không đọc được role đang lưu trong localStorage.
 *
 * Dùng null làm snapshot phía server để chưa render menu theo role
 * cho đến khi dữ liệu session được đọc ở phía trình duyệt.
 */
function getServerRole(): Role | null {
  return null;
}

/**
 * Xác định mục menu đại diện cho URL hiện tại.
 *
 * Một mục được xem là khớp khi:
 * - URL trùng hoàn toàn với href.
 * - URL nằm bên dưới href và được ngăn cách bằng dấu "/".
 *
 * Khi nhiều mục cùng khớp, chọn href dài nhất.
 * Ví dụ: /prescriptions/new phải thuộc "Kê đơn",
 * không đồng thời đánh dấu "Tra đơn".
 */
function getActiveHref(pathname: string): string | undefined {
  return navigation
    .filter(
      (item) =>
        pathname === item.href ||
        pathname.startsWith(`${item.href}/`),
    )
    .sort(
      (left, right) => right.href.length - left.href.length,
    )[0]?.href;
}

/**
 * Menu con của phân hệ Dược.
 *
 * Trách nhiệm:
 * - Đọc role từ session phía client.
 * - Chỉ hiển thị những mục mà role có capability tương ứng.
 * - Đánh dấu mục đang mở dựa trên URL.
 *
 * Không chịu trách nhiệm bảo vệ API hoặc chặn truy cập trực tiếp
 * vào route. Những kiểm tra đó không thể thay thế bằng việc ẩn menu.
 */
export function PharmacyNav() {
  const pathname = usePathname();

  // getRole là snapshot phía trình duyệt;
  // getServerRole là snapshot dùng ở phía server.
  const role = useSyncExternalStore(
    subscribeToRoleChanges,
    getRole,
    getServerRole,
  );

  const capabilities = getPharmacyCapabilities(role);

  // Lọc menu theo permission model đã được triển khai ở PH-FE-00.
  const visibleItems = navigation.filter(
    (item) => capabilities[item.capability],
  );

  // Tính route đang active từ toàn bộ danh sách cấu hình.
  // Không tính riêng từ visibleItems để tránh đánh dấu nhầm
  // khi người dùng gõ trực tiếp một URL không thuộc quyền của họ.
  const activeHref = getActiveHref(pathname);

  // Chưa có role thì chưa render menu.
  // Việc điều hướng người chưa đăng nhập do dashboard layout xử lý.
  if (role === null) {
    return null;
  }

  if (visibleItems.length === 0) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        Tài khoản của bạn không có quyền sử dụng phân hệ Dược.
      </p>
    );
  }

  return (
    <nav
      aria-label="Điều hướng phân hệ Dược"
      className="flex flex-wrap items-center gap-2"
    >
      {visibleItems.map((item) => {
        const active = item.href === activeHref;

        return (
          <Link
            key={item.href}
            href={item.href}
            // "page": đang ở chính trang của mục menu.
            // "location": đang ở một trang con của mục menu.
            aria-current={
              active
                ? pathname === item.href
                  ? "page"
                  : "location"
                : undefined
            }
            className={[
              "rounded-lg px-3 py-2 text-sm font-medium transition-colors",
              "focus-visible:outline-2 focus-visible:outline-offset-2",
              "focus-visible:outline-primary",
              active
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:bg-surface-muted hover:text-foreground",
            ].join(" ")}
          >
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}