import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ScheduleDateInput } from "./ScheduleDateInput";

it("keeps the ISO wire value and accessible name while explaining the selected Vietnamese date", () => {
  const change=vi.fn();
  render(<ScheduleDateInput label="Ngày làm việc" value="2026-11-04" required onChange={change}/>);
  const input=screen.getByLabelText("Ngày làm việc",{exact:true});
  expect(input).toHaveValue("2026-11-04");
  expect(input).toBeRequired();
  expect(input).toHaveAccessibleDescription(/04\/11\/2026.*ngày\/tháng\/năm/);
  expect(screen.getByText(/Đã chọn: 04\/11\/2026/)).toBeVisible();
  fireEvent.change(input,{target:{value:"2026-11-05"}});
  expect(change).toHaveBeenCalledTimes(1);
});

it("shows an empty selection after the user clears the date", () => {
  render(<ScheduleDateInput label="Từ ngày" value="" onChange={()=>{}}/>);
  expect(screen.getByLabelText("Từ ngày",{exact:true})).toHaveValue("");
  expect(screen.getByText("Chưa chọn ngày")).toBeInTheDocument();
});
