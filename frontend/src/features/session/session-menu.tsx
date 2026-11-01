"use client";

import { useEffect, useRef, useState } from "react";
import { getJson } from "@/lib/api";
import type { DemoUser } from "@/lib/contracts";
import { useSession } from "./session-provider";

export function SessionMenu() {
  const session = useSession();
  const dialog = useRef<HTMLDialogElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [users, setUsers] = useState<DemoUser[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    let current = true;
    getJson<DemoUser[]>("/api/demo/users", controller.signal)
      .then((value) => { if (current) { setUsers(value); setError(null); } })
      .catch(() => { if (current) setError("演示身份列表不可用，入口可能已关闭。"); });
    return () => { current = false; controller.abort(); };
  }, [open, attempt]);
  function close() { dialog.current?.close(); setOpen(false); trigger.current?.focus(); }

  return <>
    <button ref={trigger} className="identity-button" onClick={() => { setOpen(true); dialog.current?.showModal(); }}>
      <span aria-hidden="true">◉</span> {session.user?.displayName ?? (session.status === "loading" ? "正在确认身份" : "选择演示身份")}
    </button>
    <dialog ref={dialog} className="identity-dialog" aria-labelledby="identity-title"
      onCancel={(event) => { event.preventDefault(); close(); }} onClose={() => setOpen(false)}>
      <div className="panel-heading"><h2 id="identity-title">演示身份</h2><button onClick={close} aria-label="关闭身份选择">×</button></div>
      <p className="muted">A / B 为公开演示身份，不是私人账户。切换身份会清空当前上传草稿，已保存素材仍归原身份所有。</p>
      {error ? <div role="alert"><p>{error}</p><button onClick={() => setAttempt((n) => n + 1)}>重试身份列表</button></div>
        : users.length === 0 ? <p role="status">正在读取身份列表…</p>
        : <div className="identity-options">{users.map((user) => <button key={user.id}
          aria-pressed={session.user?.id === user.id} disabled={session.status === "switching"}
          onClick={() => { void session.select(user.id); }}>
          <span className="avatar" aria-hidden="true">{user.displayName.slice(-1)}</span>{user.displayName}
          {session.user?.id === user.id && <span>当前身份</span>}
        </button>)}</div>}
      <p role="status">{session.status === "switching" ? "正在切换身份…" : session.user ? "当前：" + session.user.displayName : "请先选择身份再上传"}</p>
      {session.error && <p role="alert">{session.error}</p>}
      <button className="primary-button" onClick={close}>完成</button>
    </dialog>
  </>;
}
