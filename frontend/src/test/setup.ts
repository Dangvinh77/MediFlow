import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

const storageValues = new WeakMap<Storage, Map<string, string>>();

function valuesFor(storage: Storage) {
  let values = storageValues.get(storage);
  if (!values) { values = new Map(); storageValues.set(storage, values); }
  return values;
}

if (typeof Storage !== "undefined") {
  Object.defineProperties(Storage.prototype, {
    length: { configurable: true, get(this: Storage) { return valuesFor(this).size; } },
    clear: { configurable: true, value(this: Storage) { valuesFor(this).clear(); } },
    getItem: { configurable: true, value(this: Storage, key: string) { return valuesFor(this).get(String(key)) ?? null; } },
    key: { configurable: true, value(this: Storage, index: number) { return Array.from(valuesFor(this).keys())[index] ?? null; } },
    removeItem: { configurable: true, value(this: Storage, key: string) { valuesFor(this).delete(String(key)); } },
    setItem: { configurable: true, value(this: Storage, key: string, value: string) { valuesFor(this).set(String(key), String(value)); } },
  });
}

function memoryStorage(): Storage {
  const storage = Object.create(Storage.prototype) as Storage;
  valuesFor(storage);
  return storage;
}

if (typeof window !== "undefined") {
  Object.defineProperty(globalThis, "localStorage", { configurable: true, writable: true, value: memoryStorage() });
  Object.defineProperty(globalThis, "sessionStorage", { configurable: true, writable: true, value: memoryStorage() });
}

afterEach(() => cleanup());
