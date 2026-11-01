import { describe, expect, it, vi } from "vitest";
import { ApiError, getJson } from "./api";

describe("Java API boundary", () => {
  it("preserves structured errors instead of turning them into successful data", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: "NOT_FOUND", message: "模板不存在" }), { status: 404 },
    )));
    await expect(getJson("/api/templates/missing")).rejects.toMatchObject({
      name: "ApiError", status: 404, code: "NOT_FOUND", message: "模板不存在",
    });
  });
  it("handles proxy HTML errors without leaking parsing errors", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("<html>502</html>", { status: 502 })));
    await expect(getJson("/api/templates")).rejects.toBeInstanceOf(ApiError);
  });
  it("forwards cancellation, avoids caches, and uses the same-origin session", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("[]"));
    vi.stubGlobal("fetch", fetchMock);
    const signal = new AbortController().signal;
    await expect(getJson("/api/templates", signal)).resolves.toEqual([]);
    expect(fetchMock).toHaveBeenCalledWith("/api/templates", expect.objectContaining({
      signal, cache: "no-store", credentials: "same-origin",
    }));
  });
});
