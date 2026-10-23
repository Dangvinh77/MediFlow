import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { useUnsavedChanges } from "./use-unsaved-changes";
function Form() {
  const guard = useUnsavedChanges();
  return <form onChangeCapture={guard.markDirty}><input aria-label="Draft" /><a href="/other">Other</a><button type="button" onClick={guard.markSaved}>Saved</button></form>;
}
afterEach(() => vi.restoreAllMocks());
it("warns for a changed form, cancels link navigation, and clears only after confirmed save", () => {
  vi.spyOn(window, "confirm").mockReturnValue(false);
  render(<Form />);
  fireEvent.change(screen.getByLabelText("Draft"), { target: { value: "Pending draft" } });
  const unload = new Event("beforeunload", { cancelable: true });
  act(() => { window.dispatchEvent(unload); });
  expect(unload.defaultPrevented).toBe(true);
  expect(fireEvent.click(screen.getByRole("link"))).toBe(false);
  expect(window.confirm).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "Saved" }));
  const savedUnload = new Event("beforeunload", { cancelable: true });
  act(() => { window.dispatchEvent(savedUnload); });
  expect(savedUnload.defaultPrevented).toBe(false);
});

it("blocks browser traversal before route listeners discard a dirty draft and restores on cancel", () => {
  history.replaceState({}, "", "/draft");
  const forward = vi.spyOn(history, "forward").mockImplementation(() => {});
  const back = vi.spyOn(history, "back").mockImplementation(() => {});
  vi.spyOn(window, "confirm").mockReturnValue(false);
  const view = render(<Form />);
  fireEvent.change(screen.getByLabelText("Draft"), { target: { value: "Keep me" } });
  const routeListener = vi.fn();
  window.addEventListener("popstate", routeListener);
  try {
    act(() => { window.dispatchEvent(new PopStateEvent("popstate", { state: {} })); });
    expect(window.confirm).toHaveBeenCalledTimes(1);
    expect(forward).toHaveBeenCalledTimes(1);
    expect(back).not.toHaveBeenCalled();
    expect(routeListener).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Draft")).toHaveValue("Keep me");
    act(() => { window.dispatchEvent(new PopStateEvent("popstate", { state: history.state })); });
    expect(window.confirm).toHaveBeenCalledTimes(1);
    vi.mocked(window.confirm).mockReturnValue(true);
    act(() => { window.dispatchEvent(new PopStateEvent("popstate", { state: {} })); });
    expect(back).toHaveBeenCalledTimes(1);
  } finally { window.removeEventListener("popstate", routeListener); view.unmount(); history.replaceState({}, "", "/"); }
});

it("consumes the guard entry on a confirmed save navigation", () => {
  history.replaceState({}, "", "/draft");
  const view = render(<Form />);
  fireEvent.change(screen.getByLabelText("Draft"), { target: { value: "Saved value" } });
  const length = history.length;
  fireEvent.click(screen.getByRole("button", { name: "Saved" }));
  act(() => { history.pushState({ __NA: true }, "", "/saved"); });
  expect(history.length).toBe(length);
  expect(location.pathname).toBe("/saved");
  expect(history.state.__mediflowDraftGuard).toBeUndefined();
  view.unmount(); history.replaceState({}, "", "/");
});