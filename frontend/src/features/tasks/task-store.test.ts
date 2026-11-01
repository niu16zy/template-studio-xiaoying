import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TaskStore } from "./task-store";
import { taskFixture as task } from "./task-fixture";
import type { Task } from "@/lib/contracts";

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const flush = async () => { await vi.advanceTimersByTimeAsync(0); };
let stores: TaskStore[];
function create() { const store = new TaskStore(); stores.push(store); return store; }
function serve(initial: Task[], read: (id: string) => Response | Promise<Response>, active = initial, cursor: string | null = null) {
  const fetcher = vi.fn((path: string) => {
    if (path === "/api/tasks/active") return Promise.resolve(json(active));
    if (path.startsWith("/api/tasks?")) return Promise.resolve(json({ items: initial, nextCursor: cursor }));
    return Promise.resolve(read(path.split("/").at(-1)!));
  });
  vi.stubGlobal("fetch", fetcher); return fetcher;
}
beforeEach(() => { vi.useFakeTimers(); stores = []; });
afterEach(() => { stores.forEach((store) => store.stop()); vi.useRealTimers(); });

describe("personal task query lifecycle", () => {
  it("keeps success, failure and running tasks independent and only polls unfinished tasks", async () => {
    const fetcher = serve([task("a"), task("b"), task("c")], (id) => json(task(id, id === "a"
      ? { status: "SUCCEEDED", resultUrl: "/a.png", version: 7 }
      : id === "b" ? { status: "FAILED", failureStage: "GENERATE", errorMessage: "模拟失败", version: 4 }
        : { status: "RUNNING", currentStage: "GENERATE", version: 2 })));
    const store = create(); store.start(); await flush();
    await vi.advanceTimersByTimeAsync(2000);
    expect(store.getSnapshot().entries.a.task.resultUrl).toBe("/a.png");
    expect(store.getSnapshot().entries.b.task.errorMessage).toBe("模拟失败");
    expect(store.getSnapshot().entries.c.task.status).toBe("RUNNING");
    fetcher.mockClear(); await vi.advanceTimersByTimeAsync(6000);
    expect(fetcher.mock.calls.map(([url]) => url)).toEqual(["/api/tasks/c", "/api/tasks/c", "/api/tasks/c"]);
  });
  it("preserves RUNNING on a query 503, pauses that query and resumes manually", async () => {
    let recovered = false;
    const fetcher = serve([task("a", { status: "RUNNING", version: 2 })], () => recovered ? json(task("a", { status: "SUCCEEDED", version: 7 })) : json({ message: "offline" }, 503));
    const store = create(); store.start(); await flush(); await vi.advanceTimersByTimeAsync(2000);
    expect(store.getSnapshot().entries.a.task.status).toBe("RUNNING");
    expect(store.getSnapshot().entries.a.queryError).toContain("不代表制作失败");
    const count = fetcher.mock.calls.length; await vi.advanceTimersByTimeAsync(20_000);
    expect(fetcher).toHaveBeenCalledTimes(count);
    recovered = true; await store.retry("a");
    expect(store.getSnapshot().entries.a.task.status).toBe("SUCCEEDED");
    expect(store.getSnapshot().entries.a.queryError).toBeNull();
    await vi.advanceTimersByTimeAsync(10_000); expect(fetcher).toHaveBeenCalledTimes(count + 1);
  });
  it("times out a hanging request without changing the task to FAILED", async () => {
    serve([task("a")], () => json({}));
    const store = create(); store.start(); await flush();
    vi.stubGlobal("fetch", vi.fn((_path: string, init: RequestInit) => new Promise((_resolve, reject) => init.signal!.addEventListener("abort", () => reject(new DOMException("Timeout", "AbortError"))))));
    await vi.advanceTimersByTimeAsync(17_000);
    expect(store.getSnapshot().entries.a.task.status).toBe("QUEUED");
    expect(store.getSnapshot().entries.a.queryError).toBeTruthy();
  });
  it("recovers active tasks outside history page one, merges pagination without duplicates and restores on remount", async () => {
    const old = task("old"); const done = task("done", { status: "SUCCEEDED", version: 7 });
    const fetcher = serve([done], () => json(old), [old], "next token");
    const store = create(); store.start(); await flush();
    expect(Object.keys(store.getSnapshot().entries).sort()).toEqual(["done", "old"]);
    fetcher.mockImplementationOnce(async (url: string) => { expect(url).toContain("cursor=next%20token"); return json({ items: [old, task("older", { status: "FAILED", version: 3 })], nextCursor: null }); });
    await store.loadMore(); expect(Object.keys(store.getSnapshot().entries)).toHaveLength(3);
    store.stop(); const restored = create(); restored.start(); await flush();
    expect(restored.getSnapshot().entries.old.task.status).toBe("QUEUED");
    await vi.advanceTimersByTimeAsync(2000);
    expect(fetcher.mock.calls.some(([url]) => url === "/api/tasks/old")).toBe(true);
  });
  it("ignores a late older snapshot and never regresses a terminal task", async () => {
    let resolve!: (value: Response) => void;
    serve([task("a")], () => new Promise((done) => { resolve = done; }));
    const store = create(); store.start(); await flush(); await vi.advanceTimersByTimeAsync(2000);
    store.accept(task("a", { status: "SUCCEEDED", version: 7, resultUrl: "/a.png" }));
    resolve(json(task("a", { status: "RUNNING", version: 1 }))); await flush();
    store.accept(task("a", { status: "RUNNING", version: 9 }));
    expect(store.getSnapshot().entries.a.task.status).toBe("SUCCEEDED");
    expect(store.getSnapshot().entries.a.task.version).toBe(7);
  });
  it("aborts in-flight queries and ignores old-user responses even if fetch ignores abort", async () => {
    let resolve!: (value: Response) => void;
    serve([task("a")], () => new Promise((done) => { resolve = done; }));
    const first = create(); first.start(); await flush(); await vi.advanceTimersByTimeAsync(2000);
    first.stop();
    const fetcher = serve([task("b", { status: "SUCCEEDED", version: 7 })], () => json({}), []);
    const second = create(); second.start(); await flush();
    resolve(json(task("a", { status: "SUCCEEDED", version: 7 }))); await flush();
    expect(Object.keys(second.getSnapshot().entries)).toEqual(["b"]);
    expect(first.getSnapshot().entries.a.task.status).toBe("QUEUED");
    const count = fetcher.mock.calls.length; await vi.advanceTimersByTimeAsync(20_000); expect(fetcher).toHaveBeenCalledTimes(count);
  });
  it("retains existing history on failed refresh and has no polling when empty", async () => {
    const fetcher = serve([], () => json({}), []);
    const store = create(); store.start(); await flush(); await vi.advanceTimersByTimeAsync(20_000);
    expect(fetcher).toHaveBeenCalledTimes(2);
    store.accept(task("a", { status: "SUCCEEDED", version: 7 }));
    fetcher.mockRejectedValue(new TypeError("offline")); await store.refresh();
    expect(store.getSnapshot().entries.a.task.status).toBe("SUCCEEDED");
    expect(store.getSnapshot().historyError).toBeTruthy(); expect(store.getSnapshot().activeError).toBeTruthy();
  });
  it("cleans every request and timer on leaving the page", async () => {
    const signals: AbortSignal[] = [];
    serve([task("a")], () => json({})); const store = create(); store.start(); await flush();
    vi.stubGlobal("fetch", vi.fn((_url: string, init: RequestInit) => { signals.push(init.signal!); return new Promise(() => {}); }));
    await vi.advanceTimersByTimeAsync(2000); store.stop();
    expect(signals[0].aborted).toBe(true); expect(vi.getTimerCount()).toBe(0);
  });
});
