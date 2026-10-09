import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { AdmissionDTO } from "../types";

const { createAdmission, replace } = vi.hoisted(() => ({
  createAdmission: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
}));

vi.mock("../api", () => ({
  inpatientApi: { createAdmission },
}));

import { CreateAdmissionForm } from "./CreateAdmissionForm";

const createdAdmission = {
  maDotNoiTru: "00000000-0000-4000-8000-000000000006",
  maYeuCauNoiTru: "00000000-0000-4000-8000-000000000001",
  maBenhNhan: "00000000-0000-4000-8000-000000000003",
  maHoSoNguon: "00000000-0000-4000-8000-000000000002",
  maKhoa: "00000000-0000-4000-8000-000000000004",
  doUuTien: "URGENT",
  capCuu: false,
  status: "AWAITING_BED",
  maGiuongDangSuDung: null,
  maXacNhanTamUng: null,
  maQuyetToan: null,
  maTomTatRaVien: null,
  maPheDuyetNgoaiLe: null,
  thoiGianYeuCau: "2026-10-09T03:30:00.000Z",
  thoiGianNhapVien: null,
  thoiGianRaVienYTe: null,
  thoiGianDong: null,
  thoiGianHuy: null,
  lyDoHuy: null,
  yeuLenhNgoai: [],
} satisfies AdmissionDTO;

async function fillValidForm() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/^Mã yêu cầu nội trú/), createdAdmission.maYeuCauNoiTru);
  await user.type(screen.getByLabelText(/^Mã hồ sơ nguồn/), createdAdmission.maHoSoNguon);
  await user.type(screen.getByLabelText(/^Mã bệnh nhân/), createdAdmission.maBenhNhan);
  await user.type(screen.getByLabelText(/^Mã khoa/), createdAdmission.maKhoa);
  await user.type(screen.getByLabelText(/^Mã nhân viên yêu cầu/), "00000000-0000-4000-8000-000000000005");
  await user.type(screen.getByLabelText(/^Tóm tắt chẩn đoán/), "Viêm phổi");
  await user.selectOptions(screen.getByLabelText(/^Mức độ ưu tiên/), "URGENT");
  await user.type(screen.getByLabelText(/^Thời gian yêu cầu/), "2026-10-09T10:30");
  return user;
}

describe("CreateAdmissionForm", () => {
  beforeEach(() => {
    createAdmission.mockReset();
    replace.mockReset();
  });

  it("does not submit malformed identifiers", async () => {
    const user = userEvent.setup();
    render(<CreateAdmissionForm />);

    await user.type(screen.getByLabelText(/^Mã yêu cầu nội trú/), "bad-id");
    await user.click(screen.getByRole("button", { name: "Tạo đợt nội trú" }));

    expect(await screen.findByText("Mã yêu cầu nội trú phải là UUID hợp lệ.")).toBeVisible();
    expect(createAdmission).not.toHaveBeenCalled();
  });

  it("submits the exact payload and opens the created admission", async () => {
    createAdmission.mockResolvedValue(createdAdmission);
    render(<CreateAdmissionForm />);
    const user = await fillValidForm();

    await user.click(screen.getByRole("button", { name: "Tạo đợt nội trú" }));

    await waitFor(() => expect(createAdmission).toHaveBeenCalledWith({
      maYeuCauNoiTru: createdAdmission.maYeuCauNoiTru,
      maHoSoNguon: createdAdmission.maHoSoNguon,
      maBenhNhan: createdAdmission.maBenhNhan,
      maKhoa: createdAdmission.maKhoa,
      nguoiYeuCau: "00000000-0000-4000-8000-000000000005",
      tomTatChanDoan: "Viêm phổi",
      doUuTien: "URGENT",
      capCuu: false,
      thoiGianYeuCau: new Date("2026-10-09T10:30").toISOString(),
    }));
    expect(replace).toHaveBeenCalledWith(`/inpatient/${createdAdmission.maDotNoiTru}?notice=created`);
  });

  it("redirects an expired session to login", async () => {
    createAdmission.mockRejectedValue(new ApiRequestError("Hết phiên", 401));
    render(<CreateAdmissionForm />);
    const user = await fillValidForm();

    await user.click(screen.getByRole("button", { name: "Tạo đợt nội trú" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });

  it("keeps conflict errors in context with their correlation id", async () => {
    createAdmission.mockRejectedValue(new ApiRequestError(
      "Yêu cầu nội trú đã tồn tại",
      409,
      "ADMISSION_REQUEST_CONFLICT",
      [],
      "corr-409",
    ));
    render(<CreateAdmissionForm />);
    const user = await fillValidForm();

    await user.click(screen.getByRole("button", { name: "Tạo đợt nội trú" }));

    expect(await screen.findByText("Yêu cầu nội trú đã tồn tại")).toBeVisible();
    expect(screen.getByText(/corr-409/)).toBeVisible();
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });

  it("offers retry only for retryable server failures", async () => {
    createAdmission.mockRejectedValue(new ApiRequestError("Dịch vụ tạm thời gián đoạn", 503));
    render(<CreateAdmissionForm />);
    const user = await fillValidForm();

    await user.click(screen.getByRole("button", { name: "Tạo đợt nội trú" }));

    expect(await screen.findByRole("button", { name: "Thử lại" })).toBeVisible();
  });
});
