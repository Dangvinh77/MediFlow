"use client";

import { useState } from "react";
import { MediFlowLoader } from "@mediflow/loader";

type Mode = "dark" | "light";

const THEME = {
  dark: {
    background: "bg-slate-950",
    loaderColor: "#2DD4BF",
    trailColor: "#2DD4BF",
    textClass: "text-white",
    buttonClass:
      "bg-slate-800 text-white hover:bg-slate-700 border-slate-700",
  },
  light: {
    // Vàng nhạt sáng bóng hoàng kim
    background: "bg-gradient-to-b from-amber-50 via-yellow-100 to-amber-100",
    // Xanh lục đậm
    loaderColor: "#166534",
    trailColor: "#166534",
    textClass: "text-green-900",
    buttonClass:
      "bg-amber-200 text-green-900 hover:bg-amber-300 border-amber-300",
  },
} as const;

export default function LoadingTestPage() {
  const [mode, setMode] = useState<Mode>("dark");
  const theme = THEME[mode];

  const toggleMode = () => {
    setMode((prev) => (prev === "dark" ? "light" : "dark"));
  };

  return (
    <main
      className={`relative flex min-h-screen items-center justify-center transition-colors duration-300 ${theme.background}`}
    >
      {/* Nút toggle mode — góc phải trên */}
      <button
        type="button"
        onClick={toggleMode}
        aria-label={`Chuyển sang ${mode === "dark" ? "light" : "dark"} mode`}
        className={`absolute right-6 top-6 rounded-full border px-4 py-2 text-sm font-medium shadow-sm transition-colors ${theme.buttonClass}`}
      >
        {mode === "dark" ? "☀️ Light mode" : "🌙 Dark mode"}
      </button>

      <MediFlowLoader
        className={`flex flex-col items-center gap-4 text-sm transition-colors duration-300 ${theme.textClass}`}
        color={theme.loaderColor}
        glow
        flow={false}
        holdDuration={0.6}
        label="Loading..."
        size={160}
        slideDuration={1}
        speed={1.5}
        stagger={0.1}
        trailColor={theme.trailColor}
      />
    </main>
  );
}
