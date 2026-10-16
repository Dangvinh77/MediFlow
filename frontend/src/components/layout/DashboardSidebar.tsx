"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { Role } from "@/lib/roles";

interface NavigationItem {
  group: string;
  href: string;
  label: string;
  eyebrow: string;
  roles: readonly Role[];
}

const navigation: readonly NavigationItem[] = [
  { group: "Tổ chức & bệnh nhân", href: "/organization", label: "Tổ chức", eyebrow: "Nhân sự & khoa", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE"] },
  { group: "Tổ chức & bệnh nhân", href: "/patients", label: "Bệnh nhân", eyebrow: "Hồ sơ định danh", roles: ["ADMIN", "DOCTOR", "NURSE"] },
  { group: "Khám ngoại trú", href: "/appointments", label: "Lịch hẹn", eyebrow: "Tiếp nhận ngoại trú", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE"] },
  { group: "Khám ngoại trú", href: "/records", label: "Hồ sơ khám", eyebrow: "Chẩn đoán & kết luận", roles: ["ADMIN", "DOCTOR", "NURSE"] },
  { group: "Xét nghiệm", href: "/lab", label: "Xét nghiệm", eyebrow: "Hàng đợi & kết quả", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE", "LAB_TECH"] },
  { group: "Dược", href: "/pharmacy", label: "Dược", eyebrow: "Thuốc & đơn thuốc", roles: ["ADMIN", "DOCTOR", "PHARMACIST"] },
  { group: "Viện phí", href: "/billing", label: "Viện phí", eyebrow: "Hóa đơn & thanh toán", roles: ["ADMIN", "CASHIER"] },
  { group: "Báo cáo & thông báo", href: "/notifications", label: "Thông báo", eyebrow: "Lịch sử gửi", roles: ["ADMIN", "NURSE", "PATIENT"] },
  { group: "Báo cáo & thông báo", href: "/reports", label: "Báo cáo", eyebrow: "Chỉ số vận hành", roles: ["ADMIN", "MANAGER"] },
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
        <div className="flex min-h-[4.5rem] items-center gap-3 border-b border-border px-5">
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
            {visibleNavigation.map((item, index) => {
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              return (
                <li key={item.href}>
                  {index === 0 || visibleNavigation[index - 1].group !== item.group ? (
                    <p className="px-3 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                      {item.group}
                    </p>
                  ) : null}
                  <Link
                    href={item.href}
                    aria-label={`${item.label} — ${item.eyebrow}`}
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
          MediFlow · Quản lý ngoại trú
        </div>
      </aside>
    </>
  );
}
