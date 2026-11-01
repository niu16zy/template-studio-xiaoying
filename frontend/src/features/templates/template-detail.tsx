"use client";

import Link from "next/link";
import Image from "next/image";
import { useEffect, useState } from "react";
import { ApiError, getJson } from "@/lib/api";
import type { Template } from "@/lib/contracts";
import { useSession } from "@/features/session/session-provider";
import { UploadWorkspace } from "@/features/uploads/upload-workspace";
import { TaskScope } from "@/features/tasks/task-provider";
import { TaskHistory } from "@/features/tasks/task-history";

type Detail = { status: "loading" } | { status: "error"; message: string; notFound: boolean } | { status: "ready"; template: Template };

export function TemplateDetail({ id }: { id: string }) {
  const session = useSession();
  const [detail, setDetail] = useState<Detail>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let current = true;
    const controller = new AbortController();
    getJson<Template>("/api/templates/" + encodeURIComponent(id), controller.signal)
      .then((template) => { if (current) setDetail({ status: "ready", template }); })
      .catch((error) => { if (current) setDetail({ status: "error", notFound: error instanceof ApiError && error.status === 404,
        message: error instanceof Error ? error.message : "模板读取失败" }); });
    return () => { current = false; controller.abort(); };
  }, [id, attempt]);

  if (detail.status === "loading") return <main id="main"><div className="notice" role="status">正在读取模板要求…</div></main>;
  if (detail.status === "error") return <main id="main"><div className="notice" role="alert">
    <h1>{detail.notFound ? "模板不存在" : "暂时无法读取模板"}</h1><p>{detail.message}</p>
    {!detail.notFound && <button onClick={() => { setDetail({ status: "loading" }); setAttempt((n) => n + 1); }}>重试</button>} <Link href="/">返回模板列表</Link>
  </div></main>;
  const template = detail.template;
  return <TaskScope><main id="main" className="detail-layout">
    <aside className="creation-panel">
      <Link href="/" className="back-link">‹ 返回模板列表</Link>
      <h1>{template.title}</h1>
      <div className="detail-cover"><Image src={template.coverUrl} alt={template.title + "示例"} fill unoptimized sizes="(max-width: 800px) 90vw, 400px" priority /></div>
      <p className="template-description">{template.description}</p>
      {session.user && session.status === "ready" ? <UploadWorkspace key={template.id + ":" + session.user.id + ":" + session.epoch} template={template} userId={session.user.id} />
        : <section className="identity-prompt"><h2>{session.status === "switching" ? "正在切换身份…" : "先选择演示身份"}</h2>
          <p>使用右上角的身份入口选择 A 或 B。素材会保存在对应身份下。</p>
          {(session.status === "error" || session.error) && <><p role="alert">{session.error}</p><button onClick={() => { void session.refresh(); }}>重新确认身份</button></>}
        </section>}
    </aside>
    <TaskHistory />
  </main></TaskScope>;
}
