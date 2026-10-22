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
