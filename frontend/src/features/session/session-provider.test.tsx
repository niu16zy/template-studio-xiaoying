import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SessionProvider, useSession } from "./session-provider";

function Probe() {
  const session = useSession();
  return <><span data-testid="user">{session.user?.id ?? session.status}</span>
    <button onClick={() => { void session.select("b"); }}>选择 B</button>
    <button onClick={() => { void session.refresh(); }}>确认身份</button></>;
}

describe("session request scope", () => {
  it("does not let a delayed old-user read overwrite a completed switch", async () => {
    let resolveOld!: (value: Response) => void;
    vi.stubGlobal("fetch", vi.fn()
      .mockReturnValueOnce(new Promise<Response>((resolve) => { resolveOld = resolve; }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "b", displayName: "B" }))));
    render(<SessionProvider><Probe /></SessionProvider>);
    await waitFor(() => expect(resolveOld).toBeDefined());
    fireEvent.click(screen.getByText("选择 B"));
    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("b"));
    await act(async () => { resolveOld(new Response(JSON.stringify({ id: "a", displayName: "A" }))); });
    expect(screen.getByTestId("user")).toHaveTextContent("b");
  });

  it("restores a cookie-backed identity from Java when mounted again", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ id: "a", displayName: "A" })))));
    const first = render(<SessionProvider><Probe /></SessionProvider>);
    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("a"));
    first.unmount();
    render(<SessionProvider><Probe /></SessionProvider>);
    await waitFor(() => expect(screen.getByTestId("user")).toHaveTextContent("a"));
  });
});
