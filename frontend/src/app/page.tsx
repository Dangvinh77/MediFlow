import Link from "next/link";
import { ThemeToggle } from "@/components/theme/ThemeToggle";

export default function Home() {
  return (
    <main className="relative mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-8 px-6">
      <div className="absolute right-6 top-6">
        <ThemeToggle />
      </div>
      <div>
        <h1 className="text-4xl font-bold tracking-tight">MediFlow</h1>
        <p className="mt-2 text-lg text-muted-foreground">
          Hospital management — frontend (Next.js). Talks to the API gateway at{" "}
          <code className="rounded bg-surface-muted px-1.5 py-0.5 text-sm text-foreground">
            /api/v1/*
          </code>
          .
        </p>
      </div>

      <div className="flex flex-wrap gap-4">
        <Link
          href="/login"
          className="rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground transition-opacity hover:opacity-90"
        >
          Đăng nhập
        </Link>
        <Link
          href="/patients"
          className="rounded-lg border border-border bg-surface px-5 py-2.5 font-medium transition-colors hover:bg-surface-muted"
        >
          Danh sách bệnh nhân
        </Link>
        <Link
          href="/appointments"
          className="rounded-lg border border-border bg-surface px-5 py-2.5 font-medium transition-colors hover:bg-surface-muted"
        >
          Lịch hẹn
        </Link>
        <Link
          href="/records"
          className="rounded-lg border border-border bg-surface px-5 py-2.5 font-medium transition-colors hover:bg-surface-muted"
        >
          Hồ sơ khám
        </Link>
        <Link
          href="/lab"
          className="rounded-lg border border-border bg-surface px-5 py-2.5 font-medium transition-colors hover:bg-surface-muted"
        >
          Xét nghiệm
        </Link>
      </div>

      <p className="text-sm text-muted-foreground">
        Demo login: <strong>admin / admin123</strong> (stub auth on the gateway).
      </p>
    </main>
  );
}
