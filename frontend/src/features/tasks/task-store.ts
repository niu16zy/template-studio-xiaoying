import { ApiError, getJson } from "@/lib/api";
import type { Task, TaskPage } from "@/lib/contracts";

export const terminal = (task: Task) => task.status === "SUCCEEDED" || task.status === "FAILED";
export type TaskEntry = { task: Task; queryError: string | null; checking: boolean };
type Snapshot = {
  entries: Record<string, TaskEntry>;
  loading: boolean;
  activeLoading: boolean;
  loadingMore: boolean;
  historyError: string | null;
  activeError: string | null;
  nextCursor: string | null;
};
type Request = { controller: AbortController; timer: ReturnType<typeof setTimeout> };

// One store belongs to one mounted user scope. Neither cookie changes nor aborted fetches
// may let an earlier scope update the current screen. Lists and individual reads share versions.
export class TaskStore {
  private state: Snapshot = { entries: {}, loading: true, activeLoading: true, loadingMore: false, historyError: null, activeError: null, nextCursor: null };
  private listeners = new Set<() => void>();
  private running = false;
  private requests = new Map<string, Request>();
  private polls = new Map<string, ReturnType<typeof setTimeout>>();
  constructor(private onSessionError: () => void = () => {}) {}
  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  private publish(patch: Partial<Snapshot>) {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((listener) => listener());
  }
  start = () => { this.running = true; void this.refresh(); this.schedule(); };
  stop = () => {
    this.running = false;
    this.requests.forEach(({ controller, timer }) => { controller.abort(); clearTimeout(timer); });
    this.requests.clear();
    this.polls.forEach(clearTimeout);
    this.polls.clear();
  };
  private async read<T>(key: string, path: string, success: (value: T) => void, failure: (error: unknown) => void) {
    if (!this.running) return;
    const previous = this.requests.get(key);
    if (previous) { previous.controller.abort(); clearTimeout(previous.timer); }
    const controller = new AbortController();
    const request = { controller, timer: setTimeout(() => controller.abort(), 15_000) };
    this.requests.set(key, request);
    try {
      const value = await getJson<T>(path, controller.signal);
      if (this.running && this.requests.get(key) === request) success(value);
    } catch (error) {
      if (this.running && this.requests.get(key) === request) {
        failure(error);
        if (error instanceof ApiError && (error.status === 401 || error.code === "SESSION_CHANGED")) this.onSessionError();
      }
    } finally {
      clearTimeout(request.timer);
      if (this.requests.get(key) === request) this.requests.delete(key);
      this.schedule();
    }
  }
  accept = (task: Task) => { if (this.running) this.merge([task]); };
  private merge(tasks: Task[]) {
    const entries = { ...this.state.entries };
    for (const task of tasks) {
      const previous = entries[task.id];
      if (previous && (task.version < previous.task.version || (terminal(previous.task) && !terminal(task)))) continue;
      entries[task.id] = { task, queryError: null, checking: previous?.checking ?? false };
      if (terminal(task)) {
        clearTimeout(this.polls.get(task.id));
        this.polls.delete(task.id);
        const request = this.requests.get("task:" + task.id);
        if (request) { request.controller.abort(); clearTimeout(request.timer); this.requests.delete("task:" + task.id); }
        entries[task.id].checking = false;
      }
    }
    this.publish({ entries });
    this.schedule();
  }
  private schedule() {
    if (!this.running) return;
    for (const { task, queryError } of Object.values(this.state.entries)) {
      if (terminal(task) || queryError || this.polls.has(task.id) || this.requests.has("task:" + task.id)) continue;
      this.polls.set(task.id, setTimeout(() => {
        this.polls.delete(task.id);
        void this.retry(task.id);
      }, 2000));
    }
  }
  retry = async (id: string) => {
    if (!this.running || !this.state.entries[id] || this.requests.has("task:" + id)) return;
    clearTimeout(this.polls.get(id));
    this.polls.delete(id);
    this.updateEntry(id, { checking: true });
    await this.read<Task>("task:" + id, "/api/tasks/" + encodeURIComponent(id), (task) => {
      this.merge([task]);
      this.updateEntry(id, { checking: false });
    }, () => this.updateEntry(id, { checking: false, queryError: "状态更新中断，保留上次记录；这不代表制作失败。" }));
  };
  private updateEntry(id: string, patch: Partial<TaskEntry>) {
    this.publish({ entries: { ...this.state.entries, [id]: { ...this.state.entries[id], ...patch } } });
  }
  refresh = async () => {
    if (!this.running) return;
    this.publish({ loading: true, activeLoading: true });
    await Promise.allSettled([
      this.history(null),
      this.read<Task[]>("active", "/api/tasks/active", (tasks) => {
        this.merge(tasks); this.publish({ activeError: null, activeLoading: false });
      }, () => this.publish({ activeError: "未完成任务暂时无法恢复，请刷新记录重试。", activeLoading: false })),
    ]);
    // Each list settles its own state; no stale aggregate completion writes after stop/restart.
  };
  private history = async (cursor: string | null) => {
    await this.read<TaskPage>("history", "/api/tasks?limit=12" + (cursor ? "&cursor=" + encodeURIComponent(cursor) : ""), (page) => {
      this.merge(page.items);
      this.publish({ nextCursor: page.nextCursor, historyError: null, loading: false, loadingMore: false });
    }, () => this.publish({ historyError: "历史记录读取失败，已显示的记录会保留。请重试。", loading: false, loadingMore: false }));
  };
  loadMore = async () => {
    if (!this.running || !this.state.nextCursor || this.state.loading || this.state.loadingMore) return;
    this.publish({ loadingMore: true });
    await this.history(this.state.nextCursor);
  };
}
