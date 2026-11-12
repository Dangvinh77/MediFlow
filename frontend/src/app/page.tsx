
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import { MediFlowMark } from "@mediflow/loader";
import Link from "next/link";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { LanguageToggle } from "@/components/i18n/LanguageToggle";

const workspaces = [
  { title: "Tổ chức & bệnh nhân", description: "Khoa, nhân sự và hồ sơ định danh", href: "/organization" },
  { title: "Khám ngoại trú", description: "Lịch hẹn, hồ sơ khám và chẩn đoán", href: "/appointments" },
  { title: "Xét nghiệm", description: "Hàng đợi và kết quả xét nghiệm", href: "/lab" },
  { title: "Dược", description: "Danh mục thuốc, tồn kho và đơn thuốc", href: "/pharmacy" },
  { title: "Viện phí", description: "Hóa đơn và thanh toán", href: "/billing" },
  { title: "Báo cáo & thông báo", description: "Chỉ số vận hành và lịch sử thông báo", href: "/reports" },
] as const;

export default function Home() {
  return (
    <main className="mx-auto min-h-screen max-w-6xl px-4 py-8 sm:px-8 sm:py-12">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-6">
        <div className="flex items-center gap-3">
          <MediFlowMark size={44} />
          <div>
            <p className="text-xl font-semibold tracking-tight">MediFlow</p>
            <p className="text-sm text-muted-foreground"><L10n>{"Hospital workspace"}</L10n></p>
          </div>
        </div>
        <div className="grid gap-2"><ThemeToggle /><LanguageToggle /></div>
      </header>
      <div className="py-10">
        <h1 className="text-3xl font-semibold tracking-tight"><L10n>{"Không gian vận hành MediFlow"}</L10n></h1>
        <p className="mt-3 text-muted-foreground"><L10n>{"Quản lý hoạt động ngoại trú trong một không gian làm việc thống nhất."}</L10n></p>
        <Link href="/login" className="mt-6 inline-flex min-h-11 items-center rounded-lg bg-primary px-5 py-2.5 font-semibold text-primary-foreground hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"><L10n>{"Đăng nhập vào hệ thống"}</L10n></Link>
      </div>
      <section aria-label="Các nhóm nghiệp vụ" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <L10nValue>{workspaces.map((workspace) => (
          <Link key={workspace.href} href={workspace.href} className="rounded-xl border border-border bg-surface p-6 transition-colors hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
            <h2 className="font-semibold"><L10nValue>{workspace.title}</L10nValue></h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground"><L10nValue>{workspace.description}</L10nValue></p>
          </Link>
        ))}</L10nValue>
      </section>
      <p className="mt-6 text-sm text-muted-foreground"><L10n>{"Đăng nhập để xem các nghiệp vụ được cấp quyền cho bạn."}</L10n></p>
    </main>
  );
}
