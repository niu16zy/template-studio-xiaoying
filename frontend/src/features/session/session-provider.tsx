"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { ApiError, getJson, requestJson } from "@/lib/api";
import type { DemoUser } from "@/lib/contracts";

type SessionState = {
  user: DemoUser | null;
  status: "loading" | "ready" | "anonymous" | "error" | "switching";
  checking: boolean;
  epoch: number;
  error: string | null;
};
type SessionContextValue = SessionState & { refresh: () => Promise<void>; select: (id: string) => Promise<void> };
const Context = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<SessionState>({ user: null, status: "loading", checking: false, epoch: 0, error: null });
  const sequence = useRef(0);
  const reading = useRef<AbortController | null>(null);
  const switching = useRef(false);
  const mounted = useRef(true);
  const invalidateReads = useCallback(() => {
    sequence.current += 1;
    reading.current?.abort();
  }, []);

  const refresh = useCallback(async () => {
    if (switching.current || !mounted.current) return;
    const id = ++sequence.current;
    reading.current?.abort();
    const controller = new AbortController();
    reading.current = controller;
    const timeout = setTimeout(() => controller.abort(), 15_000);
    setState((old) => ({ ...old, checking: true }));
    try {
      const user = await getJson<DemoUser>("/api/session", controller.signal);
      if (id === sequence.current) setState((old) => ({
        user, status: "ready", checking: false, error: null,
        epoch: old.epoch + (old.user?.id !== user.id ? 1 : 0),
      }));
    } catch (error) {
      if (id !== sequence.current) return;
      const anonymous = error instanceof ApiError && error.status === 401;
      setState((old) => ({ user: null, status: anonymous ? "anonymous" : "error", checking: false,
        epoch: old.epoch + 1, error: anonymous ? null : "身份确认失败，请重试" }));
    } finally { clearTimeout(timeout); }
  }, []);

  const select = useCallback(async (userId: string) => {
    if (switching.current || !mounted.current) return;
    switching.current = true;
    reading.current?.abort();
    const id = ++sequence.current;
    setState((old) => ({ user: null, status: "switching", checking: true, epoch: old.epoch + 1, error: null }));
    let failure = false;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15_000);
    try {
      // A timeout is an uncertain mutation; always reconcile the cookie-backed session afterwards.
      const user = await requestJson<DemoUser>("/api/session", {
        method: "POST", signal: controller.signal, headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId }),
      });
      if (id === sequence.current) setState((old) => ({ ...old, user, status: "ready", checking: false }));
      try { localStorage.setItem("studio-session-changed", String(Date.now())); } catch { /* Focus checks still work when storage is disabled. */ }
    } catch {
      failure = true;
    } finally {
      switching.current = false;
      clearTimeout(timeout);
    }
    if (failure && mounted.current && id === sequence.current) {
      await refresh();
      setState((old) => ({ ...old, error: "切换请求未确认，已重新核对当前身份；可再次选择。" }));
    }
  }, [refresh]);

  useEffect(() => {
    mounted.current = true;
    let active = true;
    // Initial read is asynchronous; subsequent focus checks keep drafts when identity is unchanged.
    void Promise.resolve().then(() => { if (active) void refresh(); });
    const onFocus = () => { void refresh(); };
    const onStorage = (event: StorageEvent) => { if (event.key === "studio-session-changed") void refresh(); };
    window.addEventListener("focus", onFocus);
    window.addEventListener("storage", onStorage);
    return () => {
      active = false;
      mounted.current = false;
      invalidateReads();
      window.removeEventListener("focus", onFocus);
      window.removeEventListener("storage", onStorage);
    };
  }, [refresh, invalidateReads]);

  return <Context.Provider value={{ ...state, refresh, select }}>{children}</Context.Provider>;
}

export function useSession() {
  const value = useContext(Context);
  if (!value) throw new Error("SessionProvider is required");
  return value;
}
