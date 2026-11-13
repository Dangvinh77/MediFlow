import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { expect, it } from "vitest";
import { TabPanel, Tabs } from "./Tabs";

function Harness() {
  const [active, setActive] = useState("a");
  const tabs = [{ id: "a", label: "Khám", badge: 2 }, { id: "b", label: "Đơn thuốc" }, { id: "c", label: "Tài liệu" }];
  return <>
    <Tabs idPrefix="t" label="Phần hồ sơ" tabs={tabs} active={active} onChange={setActive} />
    <TabPanel idPrefix="t" id="a" active={active}><input aria-label="Nháp" /></TabPanel>
    <TabPanel idPrefix="t" id="b" active={active}>Nội dung đơn</TabPanel>
    <TabPanel idPrefix="t" id="c" active={active}>Nội dung tài liệu</TabPanel>
  </>;
}

it("switches by click and arrow keys and keeps a hidden panel's draft", () => {
  render(<Harness />);
  expect(screen.getByRole("tab", { name: /^Khám/ })).toHaveAttribute("aria-selected", "true");
  fireEvent.change(screen.getByLabelText("Nháp"), { target: { value: "đang ghi" } });
  fireEvent.click(screen.getByRole("tab", { name: "Đơn thuốc" }));
  expect(screen.getByRole("tabpanel")).toHaveTextContent("Nội dung đơn");
  fireEvent.keyDown(screen.getByRole("tab", { name: "Đơn thuốc" }), { key: "ArrowRight" });
  expect(screen.getByRole("tab", { name: "Tài liệu" })).toHaveFocus();
  fireEvent.keyDown(screen.getByRole("tab", { name: "Tài liệu" }), { key: "ArrowRight" });
  expect(screen.getByRole("tab", { name: /^Khám/ })).toHaveAttribute("aria-selected", "true");
  expect(screen.getByLabelText("Nháp")).toHaveValue("đang ghi");
});
