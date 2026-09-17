"use client";

import { useSyncExternalStore } from "react";
import { useTheme } from "./ThemeProvider";

const subscribeToHydration = () => () => undefined;

const nextTheme: Record<"light" | "dark" | "system", "light" | "dark" | "system"> = {
  light: "dark",
  dark: "system",
  system: "light",
};

const themeLabel = {
  light: "Sáng",
  dark: "Tối",
  system: "Theo hệ thống",
} as const;

export function ThemeToggle() {
  const hydrated = useSyncExternalStore(subscribeToHydration, () => true, () => false);
  const { theme, resolvedTheme, setTheme } = useTheme();

  if (!hydrated) {
    return (
      <button
        type="button"
        disabled
        aria-hidden="true"
        className="inline-flex items-center gap-2 rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-muted-foreground"
      >
        <span aria-hidden="true">◐</span>
        <span>Giao diện</span>
      </button>
    );
  }

  const target = nextTheme[theme];

  return (
    <button
      type="button"
      onClick={() => setTheme(target)}
      aria-label={`Chuyển giao diện sang ${themeLabel[target]}`}
      title={`Giao diện hiện tại: ${themeLabel[theme]}`}
      className="inline-flex items-center gap-2 rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-foreground transition-colors hover:bg-surface-muted"
    >
      <span aria-hidden="true">{resolvedTheme === "dark" ? "☀" : "☾"}</span>
      <span>{themeLabel[theme]}</span>
    </button>
  );
}
