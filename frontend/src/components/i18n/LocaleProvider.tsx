"use client";

import { createContext, useContext, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { LOCALE_STORAGE_KEY, translate, localize, type Locale } from "@/lib/l10n";

const LocaleContext = createContext<Locale>("vi");
const SetLocaleContext = createContext<(locale: Locale) => void>(() => undefined);
const subscribeHydration = () => () => undefined;

export function LocaleProvider({ children }: { children: React.ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => {
    if (typeof window === "undefined") return "vi";
    try { return localStorage.getItem(LOCALE_STORAGE_KEY) === "en" ? "en" : "vi"; } catch { return "vi"; }
  });
  const hydrated = useSyncExternalStore(subscribeHydration, () => true, () => false);
  const renderedLocale = hydrated ? locale : "vi";
  useEffect(() => {
    const receive = (event: StorageEvent) => {
      if (event.key === LOCALE_STORAGE_KEY || event.key === null) setLocaleState(event.newValue === "en" ? "en" : "vi");
    };
    window.addEventListener("storage", receive);
    return () => window.removeEventListener("storage", receive);
  }, []);
  useEffect(() => { document.documentElement.lang = renderedLocale; }, [renderedLocale]);
  const setLocale = (next: Locale) => {
    setLocaleState(next);
    try { localStorage.setItem(LOCALE_STORAGE_KEY, next); } catch { /* Keep the session preference. */ }
  };
  return <LocaleContext.Provider value={renderedLocale}><SetLocaleContext.Provider value={setLocale}>{children}</SetLocaleContext.Provider></LocaleContext.Provider>;
}

export function useLocale() {
  const locale = useContext(LocaleContext);
  const setLocale = useContext(SetLocaleContext);
  return useMemo(() => ({ locale, setLocale, t: (source: string, ...values: unknown[]) => translate(source, locale, values), localize: <T,>(value: T): T => localize(value, locale) }), [locale, setLocale]);
}

/** Render static UI copy from server components without making their route client-only. */
export function L10n({ children }: { children: string }) {
  return useLocale().localize(children);
}

export function L10nValue({ children }: { children: React.ReactNode }) {
  return useLocale().localize(children);
}
