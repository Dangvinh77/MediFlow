import { Fragment, useState, useSyncExternalStore } from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { clearSession, getSessionKey, getToken, renewAccessToken, storeSession, subscribeToAuthChanges } from "./session";

function Draft() { const [value, setValue] = useState(""); return <input aria-label="Draft" value={value} onChange={e => setValue(e.target.value)} />; }
function Workspace() {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <Fragment key={session}><Draft /></Fragment>;
}
function login(token: string) { storeSession({ accessToken: token, refreshToken: "refresh", role: "DOCTOR" }); }
beforeEach(() => { localStorage.clear(); login("first-access-token"); });
afterEach(() => { act(() => clearSession()); });

it("preserves an edited form during access renewal and resets it on another login", () => {
  render(<Workspace />);
  fireEvent.change(screen.getByLabelText("Draft"), { target: { value: "Nhân viên A chưa lưu" } });
  const session = getSessionKey()!;
  act(() => { expect(renewAccessToken(session, "renewed-access-token")).toBe(true); });
  expect(getToken()).toBe("renewed-access-token");
  expect(screen.getByLabelText("Draft")).toHaveValue("Nhân viên A chưa lưu");
  act(() => login("another-login-token"));
  expect(screen.getByLabelText("Draft")).toHaveValue("");
});

it("notifies consumers of a cross-tab logout and rejects a late renewal", () => {
  render(<Workspace />);
  const session = getSessionKey()!;
  fireEvent.change(screen.getByLabelText("Draft"), { target: { value: "Draft A" } });
  act(() => {
    localStorage.clear();
    window.dispatchEvent(new StorageEvent("storage", { key: "mediflow.accessToken", newValue: null }));
  });
  expect(screen.getByLabelText("Draft")).toHaveValue("");
  expect(renewAccessToken(session, "late-token")).toBe(false);
  expect(getSessionKey()).toBeNull();
});
