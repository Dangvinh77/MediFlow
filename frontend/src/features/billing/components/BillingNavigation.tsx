"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

interface BillingNavigationProps {
  schedulingEnabled: boolean;
}

const routes = {
  queue: { href: "/billing/payment-requests", label: "Xét nghiệm và thuốc" },
  scheduled: { href: "/billing/scheduled-payments", label: "Phí khám" },
  history: { href: "/billing", label: "Lịch sử thu" },
  refunds: { href: "/billing/refunds", label: "Hoàn tiền" },
} as const;

export function BillingNavigation({ schedulingEnabled }: BillingNavigationProps) {
  const pathname = usePathname();
  const active = pathname.startsWith(routes.queue.href)
    ? routes.queue.href
    : pathname.startsWith(routes.scheduled.href)
      ? routes.scheduled.href
      : pathname.startsWith(routes.refunds.href) ? routes.refunds.href : routes.history.href;
  const items = [routes.queue, ...(schedulingEnabled ? [routes.scheduled] : []), routes.history, routes.refunds];

  return (
    <nav aria-label="Khu vực thu ngân" className="mb-6 -mx-1 overflow-x-auto px-1">
      <div className="flex min-w-max items-center gap-5 border-b border-border">
        {items.map((item) => {
          const current = item.href === active;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={current ? "page" : undefined}
              className={`-mb-px inline-flex min-h-11 items-center border-b-2 px-1 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${current ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:border-border hover:text-foreground"}`}
            >
              {item.label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
