"use client";

import { createContext, useContext, useEffect, useState, useSyncExternalStore } from "react";
import { useSession } from "@/features/session/session-provider";
import { TaskStore } from "./task-store";

const Context = createContext<TaskStore | null>(null);
export function TaskScope({ children }: { children: React.ReactNode }) {
  const session = useSession();
  return session.status === "ready" && session.user
    ? <TaskProvider key={session.user.id + ":" + session.epoch}>{children}</TaskProvider>
    : <>{children}</>;
}
export function TaskProvider({ children }: { children: React.ReactNode }) {
  const session = useSession();
  const [store] = useState(() => new TaskStore(() => { void session.refresh(); }));
  useEffect(() => { if (!session.checking) store.start(); return store.stop; }, [store, session.checking]);
  return <Context.Provider value={store}>{children}</Context.Provider>;
}
export function useTasks() {
  const store = useContext(Context);
  if (!store) throw new Error("TaskProvider is required");
  const snapshot = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot);
  return { ...snapshot, loading: snapshot.loading || snapshot.activeLoading, store };
}
