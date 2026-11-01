import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { UploadQueue, validateFile } from "./upload-queue";
import type { Template } from "@/lib/contracts";

const template: Template = { id: "t", categoryId: "c", title: "T", description: "", coverUrl: "", sampleUrl: "",
  requiredImageCount: 2, acceptedMimeTypes: ["image/jpeg", "image/png"], maxFileSizeBytes: 5242880 };
const file = (name = "sample.png") => new File(["sample"], name, { type: "image/png" });
const response = (id: string) => new Response(JSON.stringify({ id, previewUrl: "/api/assets/" + id + "/content", mimeType: "image/png", sizeBytes: 6, width: 1, height: 1 }));
const deferred = () => {
  let resolve!: (value: Response) => void;
  const promise = new Promise<Response>((done) => { resolve = done; });
  return { promise, resolve };
};

beforeEach(() => {
  vi.useFakeTimers();
  Object.defineProperty(URL, "createObjectURL", { configurable: true, value: vi.fn((value: File) => "blob:" + value.name) });
  Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: vi.fn() });
});
afterEach(() => { vi.useRealTimers(); });

describe("independent upload slots", () => {
  it("ignores an old upload that resolves after replacement even when abort is ignored", async () => {
    const old = deferred(), replacement = deferred();
    vi.stubGlobal("fetch", vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(replacement.promise));
    const queue = new UploadQueue(template, "user-a", vi.fn());
    const first = queue.replace(0, file("old.png"));
    const second = queue.replace(0, file("new.png"));
    replacement.resolve(response("new-id"));
    await second;
    old.resolve(response("old-id"));
    await first;
    expect(queue.getSnapshot()[0].asset?.id).toBe("new-id");
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:old.png");
    queue.clear();
  });

  it("keeps errors and readiness local to each slot and supports retry", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(new Response("{}", { status: 503 }))
      .mockResolvedValueOnce(response("second")).mockResolvedValueOnce(response("retried")));
    const queue = new UploadQueue(template, "user-a", vi.fn());
    await queue.replace(0, file());
    await queue.replace(1, file());
    expect(queue.getSnapshot().map((slot) => slot.status)).toEqual(["ERROR", "READY"]);
    await queue.replace(0, file());
    expect(queue.getSnapshot().map((slot) => slot.status)).toEqual(["READY", "READY"]);
    expect(queue.getSnapshot()[1].asset?.id).toBe("second");
    queue.clear();
  });

  it("removal and identity disposal invalidate responses, revoke URLs and clear timers", async () => {
    const pending = deferred();
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(pending.promise));
    const queue = new UploadQueue(template, "user-a", vi.fn());
    const upload = queue.replace(0, file());
    queue.clear();
    expect(vi.getTimerCount()).toBe(0);
    pending.resolve(response("stale"));
    await upload;
    expect(queue.getSnapshot().every((slot) => slot.status === "EMPTY")).toBe(true);
    expect(URL.revokeObjectURL).toHaveBeenCalled();
  });

  it("rejects client-invalid images without sending and reports stale server identity", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "SESSION_CHANGED", message: "changed" }), { status: 409 }));
    vi.stubGlobal("fetch", fetcher);
    const refresh = vi.fn();
    const queue = new UploadQueue(template, "user-a", refresh);
    await queue.replace(0, new File(["x"], "bad.gif", { type: "image/gif" }));
    expect(fetcher).not.toHaveBeenCalled();
    const oversized = new File([new Uint8Array(5242881)], "large.png", { type: "image/png" });
    expect(validateFile(oversized, template)).toContain("5 MiB");
    await queue.replace(1, file());
    expect(refresh).toHaveBeenCalledOnce();
    const options = fetcher.mock.calls[0][1] as RequestInit;
    expect(new Headers(options.headers).get("X-Studio-User")).toBe("user-a");
    expect(new Headers(options.headers).get("X-Studio-Request")).toBe("1");
    queue.clear();
  });
});
