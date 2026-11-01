"use client";

import Image from "next/image";
import Link from "next/link";
import { useRef, useState } from "react";
import type { Task, TaskStage } from "@/lib/contracts";
import { useSession } from "@/features/session/session-provider";
import { useTasks } from "./task-provider";
import type { TaskEntry } from "./task-store";

const stages: Record<TaskStage, string> = { PENDING: "等待开始", ASSET_CHECK: "素材检查", GENERATE: "模拟生成", FINALIZE: "结果整理" };
const statuses = { QUEUED: "等待处理", RUNNING: "处理中", SUCCEEDED: "制作成功", FAILED: "制作失败" };
const stepStatuses = { PENDING: "待执行", RUNNING: "执行中", SUCCEEDED: "完成", FAILED: "失败", SKIPPED: "已跳过" };
function ImagePreview({ task }: { task: Task }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState(false);
  function close() { dialog.current?.close(); trigger.current?.focus(); }
  return <>
    <button ref={trigger} className="result-trigger" onClick={() => dialog.current?.showModal()} aria-label={"预览结果：" + task.templateTitle}>
      <Image unoptimized fill sizes="(max-width: 600px) 90vw, 280px" src={task.resultUrl!} alt={task.templateTitle + "制作结果"} />
      <span>查看大图 ↗</span>
    </button>
    <dialog ref={dialog} className="result-dialog" aria-label={task.templateTitle + "结果预览"} onCancel={(event) => { event.preventDefault(); close(); }}>
      <div className="panel-heading"><h2>{task.templateTitle}</h2><button autoFocus onClick={close} aria-label="关闭结果预览">×</button></div>
      <div className="result-full"><Image key={attempt} unoptimized fill sizes="90vw" src={task.resultUrl!} alt={task.templateTitle + "制作结果大图"} onError={() => setError(true)} onLoad={() => setError(false)} /></div>
      {error && <div role="alert"><p>图片暂时无法加载，任务仍为制作成功。</p><button onClick={() => { setError(false); setAttempt((n) => n + 1); }}>重新加载图片</button></div>}
    </dialog>
  </>;
}

export function TaskCard({ entry, retry }: { entry: TaskEntry; retry: () => void }) {
  const { task, queryError, checking } = entry;
  return <article className={"task-card " + task.status.toLowerCase()} aria-label={task.templateTitle + "任务 " + task.id}>
    <div className="task-card-heading"><h3>{task.templateTitle}</h3><span className={"task-status " + task.status.toLowerCase()}>{statuses[task.status]}</span></div>
    <time className="muted" dateTime={task.createdAt}>{new Date(task.createdAt).toLocaleString("zh-CN", { hour12: false })}</time>
    {task.status === "SUCCEEDED" && task.resultUrl ? <ImagePreview task={task} />
      : <div className="task-progress" aria-live="polite"><span aria-hidden="true">{task.status === "FAILED" ? "!" : task.status === "QUEUED" ? "◷" : "✳"}</span>
        <strong>{task.status === "FAILED" ? "本次制作未完成" : task.status === "QUEUED" ? "已受理，等待后台处理" : stages[task.currentStage]}</strong>
        <p>{task.status === "FAILED" ? ("失败阶段：" + stages[task.failureStage ?? "PENDING"]) : "可以继续浏览或制作，关闭页面也会继续处理。"}</p>
      </div>}
    <ol className="task-steps" aria-label="后台处理步骤">{task.steps.map((step) => <li key={step.stage} className={step.status.toLowerCase()}><span>{stages[step.stage]}</span><span>{stepStatuses[step.status]}</span></li>)}</ol>
    {task.status === "FAILED" && <p className="inline-error">{task.errorMessage ?? "任务处理失败，请查看失败阶段。"}</p>}
    {queryError && <div className="query-warning" role="alert"><p>{queryError}</p><button disabled={checking} onClick={retry}>{checking ? "正在恢复查询…" : "恢复查询"}</button></div>}
    <div className="task-card-footer"><span title={task.id}>任务 {task.id.slice(0, 8)}</span><Link href={"/templates/" + task.templateId}>查看模板 ↗</Link></div>
  </article>;
}

function PersonalTasks() {
  const { entries, loading, loadingMore, historyError, activeError, nextCursor, store } = useTasks();
  const tasks = Object.values(entries).sort((a, b) => b.task.createdAt.localeCompare(a.task.createdAt) || b.task.id.localeCompare(a.task.id));
  const unfinished = tasks.filter(({ task }) => task.status === "QUEUED" || task.status === "RUNNING").length;
  return <>
    <div className="panel-heading"><div><h2 id="history-title">我的制作记录</h2><p className="muted">{unfinished ? `${unfinished} 个任务未完成` : "没有等待中的任务"} · 当前身份的全部模板</p></div>
      <button className="refresh-tasks" disabled={loading} onClick={() => { void store.refresh(); }} aria-label="刷新制作记录">{loading ? "读取中…" : "刷新记录"}</button></div>
    {(historyError || activeError) && <div className="query-warning" role="alert"><p>{historyError}</p><p>{activeError}</p><button disabled={loading} onClick={() => { void store.refresh(); }}>重试读取记录</button></div>}
    {loading && tasks.length === 0 && <p className="empty-history" role="status">正在恢复个人记录…</p>}
    {!loading && !historyError && !activeError && tasks.length === 0 && <div className="empty-history"><span aria-hidden="true">✳</span><h3>第一份作品，从这里开始</h3><p>选择模板并上传图片，制作记录会保存在这里。</p><Link href="/">浏览模板 ↗</Link></div>}
    <div className="task-grid">{tasks.map((entry) => <TaskCard key={entry.task.id} entry={entry} retry={() => { void store.retry(entry.task.id); }} />)}</div>
    {nextCursor && <button className="load-more" disabled={loading || loadingMore} onClick={() => { void store.loadMore(); }}>{loadingMore ? "正在读取…" : "加载更早记录"}</button>}
  </>;
}
export function TaskHistory() {
  const session = useSession();
  return <section className="history-panel" aria-labelledby="history-title">
    {session.user && session.status === "ready" ? <PersonalTasks /> : <div className="empty-history"><h2 id="history-title">我的制作记录</h2>
      <p>{session.status === "loading" || session.status === "switching" ? "正在确认身份…" : "请选择右上角的演示身份，查看属于该身份的记录。"}</p>
      {session.error && <><p className="inline-error" role="alert">{session.error}</p><button onClick={() => { void session.refresh(); }}>重新确认身份</button></>}
    </div>}
  </section>;
}
