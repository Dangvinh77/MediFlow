"use client";

import { useLocale } from "./LocaleProvider";

export function LanguageToggle() {
  const { locale, setLocale, t } = useLocale();
  return <select aria-label={t("Ngôn ngữ")} value={locale} onChange={event => {
    if (event.target.value === "vi" || event.target.value === "en") setLocale(event.target.value);
  }} className="min-h-11 max-w-36 rounded-lg border border-control-border bg-surface px-3 py-2 text-sm text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
    <option value="vi" lang="vi">Tiếng Việt</option>
    <option value="en" lang="en">English</option>
  </select>;
}
