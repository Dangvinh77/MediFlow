"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { mustChangePassword } from "@/lib/session";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
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
    case "CASHIER": return schedulingEnabled ? "/billing/scheduled-payments" : "/billing";
    case "LAB_TECH": return "/lab";
    case "PHARMACIST": return "/pharmacy";
    case "MANAGER": return schedulingEnabled ? "/appointments/schedule" : "/reports";
    case "DOCTOR": return schedulingEnabled ? "/appointments/my-calendar" : "/appointments";
    case "NURSE": return schedulingEnabled ? "/appointments/reception" : "/appointments";
    case "ADMIN": return "/organization";
    default: return "/patients";
  }
}

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

  useEffect(() => {
    if (authenticated) {
      router.replace(landingPage(schedulingEnabled));
    }
  }, [authenticated, router, schedulingEnabled]);

  if (authenticated !== false) {
    return (
      <main className="relative mx-auto flex min-h-screen max-w-sm flex-col justify-center px-6 text-muted-foreground">
        Đang kiểm tra phiên đăng nhập…
      </main>
    );
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(username, password);
      router.replace(landingPage(schedulingEnabled));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Đăng nhập thất bại");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="relative mx-auto flex min-h-screen max-w-sm flex-col justify-center px-6">
      <div className="absolute right-6 top-6">
        <ThemeToggle />
      </div>
      <h1 className="text-2xl font-bold">Đăng nhập MediFlow</h1>
      <p className="mt-3 text-sm text-muted-foreground">
        Bệnh nhân đăng nhập khi cần xem kết quả khám, xét nghiệm và thông báo tái khám.
        Bạn vẫn có thể được tiếp nhận và khám tại quầy khi chưa dùng tài khoản.
      </p>
      <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm">
          Tài khoản
          <input
            className="rounded-lg border border-border bg-surface px-3 py-2 text-foreground"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
            maxLength={50}
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
      <p className="mt-4 text-sm text-muted-foreground">
        Nếu quên mật khẩu, liên hệ quầy tiếp nhận để được xác minh và hỗ trợ khôi phục
        tài khoản hiện có. Không cần tạo hồ sơ bệnh nhân mới.
      </p>
    </main>
  );
}
