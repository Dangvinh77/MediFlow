import type { Metadata } from "next";
import Script from "next/script";
import "./globals.css";
import { ThemeProvider } from "@/components/theme/ThemeProvider";

const themeInitScript = `
  (() => {
    try {
      const preference = localStorage.getItem("mediflow.theme") || "system";
      const systemDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
      const dark = preference === "dark" || (preference === "system" && systemDark);
      document.documentElement.classList.toggle("dark", dark);
      document.documentElement.style.colorScheme = dark ? "dark" : "light";
    } catch (_) {}
  })();
`;

export const metadata: Metadata = {
  title: "MediFlow",
  description: "Hệ thống quản lý bệnh viện MediFlow",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className="h-full antialiased" suppressHydrationWarning>
      <body className="min-h-full flex flex-col">
        <Script id="theme-init" strategy="beforeInteractive">
          {themeInitScript}
        </Script>
        <ThemeProvider>{children}</ThemeProvider>
      </body>
    </html>
  );
}
