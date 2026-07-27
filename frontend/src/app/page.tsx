import Link from "next/link";

export default function Home() {
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-8 px-6">
      <div>
        <h1 className="text-4xl font-bold tracking-tight">MediFlow</h1>
        <p className="mt-2 text-lg text-zinc-600 dark:text-zinc-400">
          Hospital management — frontend (Next.js). Talks to the API gateway at{" "}
          <code className="rounded bg-zinc-100 px-1.5 py-0.5 text-sm dark:bg-zinc-800">
            /api/v1/*
          </code>
          .
        </p>
      </div>

      <div className="flex flex-wrap gap-4">
        <Link
          href="/login"
          className="rounded-lg bg-black px-5 py-2.5 font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
        >
          Đăng nhập
        </Link>
        <Link
          href="/patients"
          className="rounded-lg border border-zinc-300 px-5 py-2.5 font-medium transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
        >
          Danh sách bệnh nhân
        </Link>
      </div>

      <p className="text-sm text-zinc-500">
        Demo login: <strong>admin / admin123</strong> (stub auth on the gateway).
      </p>
    </main>
  );
}
