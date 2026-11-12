import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it } from "vitest";
import { LanguageToggle } from "./LanguageToggle";
import { LocaleProvider, useLocale } from "./LocaleProvider";

function Example() {
  const { t } = useLocale();
  return <><LanguageToggle /><p>{t("Hồ sơ cá nhân")}</p></>;
}

beforeEach(() => localStorage.clear());

it("defaults to Vietnamese and persists an immediate language change", async () => {
  const view = render(<LocaleProvider><Example /></LocaleProvider>);
  expect(screen.getByText("Hồ sơ cá nhân")).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByRole("combobox", { name: "Ngôn ngữ" }), "en");
  expect(screen.getByText("Personal profile")).toBeInTheDocument();
  expect(document.documentElement.lang).toBe("en");
  expect(localStorage.getItem("mediflow.locale")).toBe("en");
  view.unmount();
  render(<LocaleProvider><Example /></LocaleProvider>);
  expect(screen.getByText("Personal profile")).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByRole("combobox", { name: "Language" }), "vi");
  expect(screen.getByText("Hồ sơ cá nhân")).toBeInTheDocument();
});

it("ignores invalid preferences and follows changes in another tab", () => {
  localStorage.setItem("mediflow.locale", "invalid");
  render(<LocaleProvider><Example /></LocaleProvider>);
  expect(screen.getByText("Hồ sơ cá nhân")).toBeInTheDocument();
  act(() => window.dispatchEvent(new StorageEvent("storage", { key: "mediflow.locale", newValue: "en" })));
  expect(screen.getByText("Personal profile")).toBeInTheDocument();
});
