import { describe, expect, it } from "vitest";
import { notificationText } from "./presentation";

describe("notificationText", () => {
  it("groups raw stored amounts the Vietnamese way", () => {
    expect(notificationText("Đã ghi nhận khoản thanh toán 36000 VND. Xem tài khoản.")).toBe("Đã ghi nhận khoản thanh toán 36.000 VND. Xem tài khoản.");
    expect(notificationText("Hoàn tiền 150000.00 VND")).toBe("Hoàn tiền 150.000 VND");
    expect(notificationText("Tổng 1250500.50 VNĐ.")).toBe("Tổng 1.250.500,5 VNĐ.");
  });

  it("leaves formatted amounts, short numbers and other digits untouched", () => {
    expect(notificationText("Đã thanh toán 36.000 VND")).toBe("Đã thanh toán 36.000 VND");
    expect(notificationText("Phí 950 VND, mã 202611130001, năm 2026")).toBe("Phí 950 VND, mã 202611130001, năm 2026");
    expect(notificationText("Gặp nurse tại quầy")).toBe("Gặp điều dưỡng tại quầy");
  });
});
