import { expect, it } from "vitest";
import english from "./en.json";
import vietnamese from "./vi.json";
import { translate, localize } from "./index";

it("keeps all interpolation slots in every translation", () => {
  expect(Object.keys(english).sort()).toEqual(Object.keys(vietnamese).sort());
  for (const [source, translation] of Object.entries(english)) {
    expect(translation.trim(), source).not.toBe("");
    expect(translation.match(/\{\d+\}/g)?.sort() ?? [], source).toEqual(source.match(/\{\d+\}/g)?.sort() ?? []);
  }
});

it("translates lower-case catalog words inside a pattern but never names", () => {
  expect(localize("Thông tin bộ phận", "en")).toBe("Unit information");
  expect(localize("Dòng tiền theo ngày", "en")).toBe("Cash flow by day");
  expect(localize("Trang sau", "en")).toBe("Next page");
  expect(localize("Chi tiết Nam", "en")).toBe("Nam details");
});

it("preserves user-entered values, unknown messages and element objects", () => {
  expect(translate("Xin chào {0}", "vi", ["Nguyễn An"])).toBe("Xin chào Nguyễn An");
  expect(localize("Nguyễn An", "en")).toBe("Nguyễn An");
  expect(localize("Unknown error from server", "en")).toBe("Unknown error from server");
  const element = { type: "input", value: "Hồ sơ cá nhân" };
  expect(localize(element, "en")).toBe(element);
  expect(localize("  Hồ sơ cá nhân  ", "en")).toBe("  Personal profile  ");
});
