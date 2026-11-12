import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { departmentContractFixture, roomTypeCatalogFixture } from "../contract.fixture";
import type { RoomDTO } from "../types";

const mocks = vi.hoisted(() => ({
  departments: vi.fn(),
  rooms: vi.fn(),
  roomTypes: vi.fn(),
  changeRoomStatus: vi.fn(),
}));

vi.mock("@/lib/auth", () => ({
  getRole: () => "ADMIN",
  getSessionKey: () => "session-a",
  subscribeToAuthChanges: () => () => {},
}));
vi.mock("../api", () => ({ organizationApi: {
  departments: mocks.departments, rooms: mocks.rooms, roomTypes: mocks.roomTypes, changeRoomStatus: mocks.changeRoomStatus,
} }));

import { RoomDirectory } from "./RoomDirectory";

const room: RoomDTO = {
  roomId: "00000000-0000-4000-8000-000000000101", departmentId: departmentContractFixture.departmentId,
  roomType: "CONSULTATION", roomNumber: "101", roomName: "Phòng khám Nội 1", zone: "Khu A",
  location: "Tầng 1, Khu A", active: true, createdAt: "2026-11-01T00:00:00Z", updatedAt: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.departments.mockResolvedValue([departmentContractFixture]);
  mocks.rooms.mockResolvedValue([room]);
  mocks.roomTypes.mockResolvedValue(roomTypeCatalogFixture);
  mocks.changeRoomStatus.mockResolvedValue({ ...room, active: false });
});

afterEach(() => vi.restoreAllMocks());

it("asks for confirmation before stopping a room and sends nothing when cancelled", async () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  render(<RoomDirectory />);
  fireEvent.click(await screen.findByRole("button", { name: "Ngừng phòng 101" }));
  expect(confirm).toHaveBeenCalledWith(expect.stringContaining("bệnh nhân đã đặt lịch tại phòng này cần được dời lịch"));
  expect(mocks.changeRoomStatus).not.toHaveBeenCalled();
  expect(screen.getByText("Đang sử dụng")).toBeVisible();
});

it("stops the room after confirmation and reopens without asking", async () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
  render(<RoomDirectory />);
  fireEvent.click(await screen.findByRole("button", { name: "Ngừng phòng 101" }));
  await waitFor(() => expect(mocks.changeRoomStatus).toHaveBeenCalledWith(room.roomId, false));
  expect(await screen.findByText("Đã ngừng sử dụng phòng.")).toBeVisible();

  confirm.mockClear();
  mocks.changeRoomStatus.mockResolvedValueOnce(room);
  fireEvent.click(screen.getByRole("button", { name: "Mở lại phòng 101" }));
  await waitFor(() => expect(mocks.changeRoomStatus).toHaveBeenLastCalledWith(room.roomId, true));
  expect(confirm).not.toHaveBeenCalled();
});
