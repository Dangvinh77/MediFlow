"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { Role } from "@/lib/roles";

interface NavigationItem {
  href: string;
  label: string;
  eyebrow: string;
  roles: readonly Role[];
}

const navigation: readonly NavigationItem[] = [
  { href: "/organization", label: "Tổ chức", eyebrow: "Nhân sự & khoa", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE"] },
  { href: "/patients", label: "Bệnh nhân", eyebrow: "Hồ sơ định danh", roles: ["ADMIN", "DOCTOR", "NURSE"] },
  { href: "/appointments", label: "Lịch hẹn", eyebrow: "Tiếp nhận ngoại trú", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE"] },
  { href: "/records", label: "Hồ sơ khám", eyebrow: "Chẩn đoán & kết luận", roles: ["ADMIN", "DOCTOR", "NURSE"] },
  { href: "/lab", label: "Xét nghiệm", eyebrow: "Hàng đợi & kết quả", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE", "LAB_TECH"] },
  { href: "/inpatient", label: "Nội trú", eyebrow: "Nhập viện & giường", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE", "CASHIER"] },
  { href: "/pharmacy", label: "Dược", eyebrow: "Thuốc & đơn thuốc", roles: ["ADMIN", "DOCTOR", "PHARMACIST"] },
  { href: "/billing", label: "Viện phí", eyebrow: "Hóa đơn & thanh toán", roles: ["ADMIN", "CASHIER"] },
  { href: "/notifications", label: "Thông báo", eyebrow: "Lịch sử gửi", roles: ["ADMIN", "NURSE", "PATIENT"] },
  { href: "/reports", label: "Báo cáo", eyebrow: "Chỉ số vận hành", roles: ["ADMIN", "MANAGER"] },
];

interface DashboardSidebarProps {
  role: Role | null;
  open: boolean;
  onClose: () => void;
}

export function DashboardSidebar({ role, open, onClose }: DashboardSidebarProps) {
  const pathname = usePathname();
  const visibleNavigation = role ? navigation.filter((item) => item.roles.includes(role)) : [];

  return (
    <>
      {open ? (
        <button
          type="button"
          aria-label="Đóng điều hướng"
          onClick={onClose}
          className="fixed inset-0 z-40 bg-slate-950/45 lg:hidden"
        />
      ) : null}
      <aside
        aria-label="Điều hướng nghiệp vụ"
        className={`${open ? "translate-x-0" : "-translate-x-full"} fixed inset-y-0 left-0 z-50 flex w-72 flex-col border-r border-border bg-surface transition-transform duration-200 lg:sticky lg:top-0 lg:z-20 lg:h-screen lg:w-60 lg:translate-x-0`}
      >
        <div className="flex min-h-16 items-center gap-3 border-b border-border px-5">
          <span className="grid size-9 place-items-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">MF</span>
          <div>
            <p className="font-semibold tracking-tight">MediFlow</p>
            <p className="text-xs text-muted-foreground">Hospital workspace</p>
          </div>
          <button
            type="button"
            aria-label="Đóng điều hướng"
            onClick={onClose}
            className="ml-auto grid size-10 place-items-center rounded-lg text-xl text-muted-foreground hover:bg-surface-muted lg:hidden"
          >
            <span aria-hidden="true">×</span>
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-4">
          <p className="px-3 pb-2 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Nghiệp vụ</p>
          <ul className="space-y-1">
            {visibleNavigation.map((item) => {
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    onClick={onClose}
                    className={`block rounded-lg border-l-2 px-3 py-2.5 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${active ? "border-primary bg-primary/10 text-foreground" : "border-transparent text-muted-foreground hover:bg-surface-muted hover:text-foreground"}`}
                  >
                    <span className="block text-sm font-semibold">{item.label}</span>
                    <span className="mt-0.5 block text-xs">{item.eyebrow}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="border-t border-border px-5 py-4 text-xs leading-5 text-muted-foreground">
          Dữ liệu đi qua Gateway và được kiểm soát lại tại từng service.
        </div>
      </aside>
    </>
  );
}
