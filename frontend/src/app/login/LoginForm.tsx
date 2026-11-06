"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { mustChangePassword } from "@/lib/session";
import { PulseLine } from "@/components/brand/PulseLine";
import { FullScreenLoader } from "@/components/ui/FullScreenLoader";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { MediFlowMark } from "@mediflow/loader";
import Link from "next/link";
import { accountSecurityApi } from "@/features/organization/security-api";
import {
  getRole,
  isAuthenticated,
  login,
  subscribeToAuthChanges,
} from "@/lib/auth";

function landingPage(schedulingEnabled: boolean) {
  if (mustChangePassword()) return "/organization/security";
  switch (getRole()) {
    case "PATIENT": return schedulingEnabled ? "/appointments/my-registration" : "/notifications";
    case "CASHIER": return schedulingEnabled ? "/billing/scheduled-payments" : "/billing/payment-requests";
    case "LAB_TECH": return "/lab";
    case "PHARMACIST": return "/pharmacy";
    case "DOCTOR": return schedulingEnabled ? "/appointments/my-calendar" : "/appointments";
    case "NURSE": return schedulingEnabled ? "/appointments/reception" : "/appointments";
    case "ADMIN": return "/organization";
    default: return "/patients";
  }
}

// How long the full-screen loader holds after a successful sign-in, before entering the system.
const SIGN_IN_HOLD_MS = 1000;

const getAuthSnapshot = (): boolean => isAuthenticated();
const getServerAuthSnapshot = (): boolean | null => null;

export function LoginForm({ schedulingEnabled = false }: { schedulingEnabled?: boolean }) {
  const router = useRouter();
  const authenticated = useSyncExternalStore(
    subscribeToAuthChanges,
    getAuthSnapshot,
    getServerAuthSnapshot,
  );
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [demoMailboxEnabled, setDemoMailboxEnabled] = useState(false);
  useEffect(() => {
    let active = true;
    accountSecurityApi.demoConfig().then(value => { if (active) setDemoMailboxEnabled(value.enabled); }).catch(() => { /* Optional demo feature. */ });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!authenticated) return;
    const enter = () => router.replace(landingPage(schedulingEnabled));
    // `loading` survives a successful submit, so this tells a fresh sign-in from a restored session.
    if (!loading) {
      enter();
      return;
    }
    const hold = setTimeout(enter, SIGN_IN_HOLD_MS);
    return () => clearTimeout(hold);
  }, [authenticated, loading, router, schedulingEnabled]);

  if (authenticated !== false) {
    return <FullScreenLoader label={loading ? "Đang vào hệ thống…" : "Đang kiểm tra phiên đăng nhập…"} />;
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(username, password);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Đăng nhập thất bại");
      setLoading(false);
    }
  }

  return (
    <main className="relative min-h-screen lg:grid lg:grid-cols-2">
      <div className="absolute right-4 top-4 z-10">
        <ThemeToggle />
      </div>
      <section aria-label="MediFlow" className="flex flex-col items-center overflow-hidden rounded-b-[2rem] bg-brand-deep pb-2 pt-12 text-center text-white lg:justify-center lg:rounded-none lg:pb-0 lg:pt-0">
        <MediFlowMark size={60} tone="on-brand" />
        <p className="mt-4 text-3xl font-extrabold tracking-tight">MediFlow</p>
        <p className="mt-1 text-sm text-white/70">Quản lý khám ngoại trú</p>
        <PulseLine />
      </section>
      <div className="mx-auto flex w-full max-w-sm flex-col justify-center px-6 py-8 lg:min-h-screen">
      <h1 className="text-2xl font-bold">Đăng nhập MediFlow</h1>
      <p className="mt-3 text-sm text-muted-foreground">
        Bệnh nhân đăng nhập khi cần xem kết quả khám, xét nghiệm và thông báo tái khám.
        Bạn vẫn có thể được tiếp nhận và khám tại quầy khi chưa dùng tài khoản.
      </p>
      <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm">
          Email
          <input
            className="rounded-lg border border-border bg-surface px-3 py-2 text-foreground"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            type="email"
            required
            maxLength={254}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Mật khẩu
          <input
            type="password"
            className="rounded-lg border border-border bg-surface px-3 py-2 text-foreground"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
            maxLength={72}
          />
        </label>

        {error && <p className="text-sm text-danger">{error}</p>}

        <button
          type="submit"
          disabled={loading}
          className="rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          {loading ? "Đang đăng nhập…" : "Đăng nhập"}
        </button>
      </form>
      <div className="mt-5 flex flex-wrap justify-between gap-4 text-sm font-medium text-primary"><Link href="/forgot-password" className="underline">Quên mật khẩu?</Link>{demoMailboxEnabled ? <Link href="/demo-mailbox" className="underline">Hộp thư demo</Link> : null}</div>
      </div>
    </main>
  );
}
