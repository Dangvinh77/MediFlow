import english from "./en.json";
import vietnamese from "./vi.json";

export type Locale = "vi" | "en";
export const LOCALE_STORAGE_KEY = "mediflow.locale";
const messages: Record<string, string> = english;
const viMessages: Record<string, string> = vietnamese;
const patterns = Object.entries(messages).filter(([source]) => /\{\d+\}/.test(source)).map(([source, target]) => {
  const slots: string[] = [];
  const pattern = source.split(/(\{\d+\})/).map(part => {
    if (/^\{\d+\}$/.test(part)) { slots.push(part); return "(.+?)"; }
    return part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }).join("");
  return { regex: new RegExp(`^${pattern}$`, "s"), target, slots };
});

export function translate(source: string, locale: Locale, values: readonly unknown[] = []): string {
  const message = (locale === "en" ? messages[source] : viMessages[source]) ?? source;
  return message.replace(/\{(\d+)\}/g, (match, index: string) =>
    Number(index) < values.length ? String(values[Number(index)]) : match,
  );
}

/** Translate UI labels and messages, retaining interpolated values verbatim. */
export function localize<T>(value: T, locale: Locale): T {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  if (locale === "vi") return (viMessages[trimmed] && viMessages[trimmed] !== trimmed ? value.replace(trimmed, () => viMessages[trimmed]) : value) as T;
  let translated = messages[trimmed];
  if (locale === "en" && translated === undefined) {
    for (const { regex, target, slots } of patterns) {
      const match = regex.exec(trimmed);
      if (match) { translated = target.replace(/\{\d+\}/g, slot => match[slots.indexOf(slot) + 1] ?? slot); break; }
    }
  }
  return (translated === undefined ? value : value.replace(trimmed, () => translated)) as T;
}
