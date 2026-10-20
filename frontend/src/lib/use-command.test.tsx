import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "./api";
import { useCommand } from "./use-command";
import { clearSession, storeSession } from "./session";

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));

function Workspace({ send, onSuccess = () => {} }: { send: () => Promise<string>; onSuccess?: (value: string) => void }) {
  const command = useCommand();
  return <><button onClick={() => void command.run(send, onSuccess)}>Send</button>
    <p role="status">{command.uncertain ? "Reconcile" : "Ready"}</p></>;
}

describe("command outcome reconciliation", () => {
  it("ignores completion from a replaced authenticated session", async () => {
    storeSession({ accessToken: "old", refreshToken: "old-refresh", role: "ADMIN" });
    let finish!: (value: string) => void;
    const send = vi.fn(() => new Promise<string>((resolve) => { finish = resolve; }));
    const onSuccess = vi.fn();
    render(<Workspace send={send} onSuccess={onSuccess} />);
    fireEvent.click(screen.getByRole("button", { name: "Send" }));
    storeSession({ accessToken: "new", refreshToken: "new-refresh", role: "ADMIN" });
    await act(async () => finish("old private result"));
    expect(onSuccess).not.toHaveBeenCalled();
    clearSession();
  });
  it.each([408, 503, 200])("locks an uncertain HTTP %s outcome against a second send", async (status) => {
    const send = vi.fn().mockRejectedValue(new ApiRequestError("Unknown outcome", status, "UNKNOWN"));
    render(<Workspace send={send} />);
    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Send" })));
    expect(screen.getByRole("status")).toHaveTextContent("Reconcile");
    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Send" })));
    expect(send).toHaveBeenCalledTimes(1);
  });

  it("allows correcting a definitive validation rejection", async () => {
    const send = vi.fn().mockRejectedValue(new ApiRequestError("Invalid", 422, "VALIDATION"));
    render(<Workspace send={send} />);
    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Send" })));
    expect(screen.getByRole("status")).toHaveTextContent("Ready");
    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Send" })));
    expect(send).toHaveBeenCalledTimes(2);
  });
});
