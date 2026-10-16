"use client";

import { useSyncExternalStore } from "react";
import { useTheme } from "./ThemeProvider";

const subscribeToHydration = () => () => undefined;

const themeLabel = {
  light: "Sáng",
  dark: "Tối",
  system: "Theo hệ thống",
} as const;

export function ThemeToggle() {
  const hydrated = useSyncExternalStore(subscribeToHydration, () => true, () => false);
  const { theme, setTheme } = useTheme();

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

  return (
    <select
      value={theme}
      onChange={(event) => {
        const value = event.target.value;
        if (value === "light" || value === "dark" || value === "system") setTheme(value);
      }}
      aria-label="Giao diện"
      className="min-h-11 max-w-36 rounded-lg border border-control-border bg-surface px-3 py-2 text-sm text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <option value="light">{themeLabel.light}</option>
      <option value="dark">{themeLabel.dark}</option>
      <option value="system">{themeLabel.system}</option>
    </select>
  );
}
