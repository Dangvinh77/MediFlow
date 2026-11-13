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
  return { regex: new RegExp(`^${pattern}$`, "s"), target, slots, literal: source.replace(/\{\d+\}/g, "").length };
// The most specific message wins: "Dòng tiền theo ngày" before "Dòng tiền theo {0}".
}).sort((a, b) => b.literal - a.literal);

/**
 * A lower-case slot holding catalog copy ("bộ phận", "ngày") is translated too. Names and codes start with a
 * capital or digit and stay verbatim, so a patient called "An" never becomes an English word.
 */
function translateSlot(value: string): string {
  const first = value.charAt(0);
  if (first === first.toUpperCase()) return value;
  return messages[value] ?? value;
}

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
      if (match) {
        translated = target.replace(/\{\d+\}/g, slot => {
          const captured = match[slots.indexOf(slot) + 1];
          return captured === undefined ? slot : translateSlot(captured);
        });
        break;
      }
    }
  }
  return (translated === undefined ? value : value.replace(trimmed, () => translated)) as T;
}
