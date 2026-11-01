"use client";

import Image from "next/image";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { ApiError, requestJson } from "@/lib/api";
import type { Task, Template } from "@/lib/contracts";
import { useSession } from "@/features/session/session-provider";
import { useTasks } from "@/features/tasks/task-provider";
import { UploadQueue } from "./upload-queue";

export function UploadWorkspace({ template, userId }: { template: Template; userId: string }) {
  const session = useSession();
  const { store } = useTasks();
  const [queue] = useState(() => new UploadQueue(template, userId, () => { void session.refresh(); }));
  const slots = useSyncExternalStore(queue.subscribe, queue.getSnapshot, queue.getSnapshot);
  const [validation, setValidation] = useState<{ busy: boolean; message: string; error: boolean }>({ busy: false, message: "", error: false });
  const [uncertain, setUncertain] = useState(false);
  const validating = useRef(false);
  const request = useRef<AbortController | null>(null);
  const deadline = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; queue.clear(); request.current?.abort(); request.current = null; clearTimeout(deadline.current); };
  }, [queue]);

  const disabled = session.checking || validation.busy || uncertain;
  const ready = slots.every((slot) => slot.status === "READY");
  function changed() { setValidation({ busy: false, message: "", error: false }); }

  async function submit() {
    if (!ready || disabled || validating.current || uncertain) return;
    validating.current = true;
    const controller = new AbortController();
    request.current = controller;
    const timeout = setTimeout(() => controller.abort(), 15_000);
    deadline.current = timeout;
    setValidation({ busy: true, message: "", error: false });
    try {
      const task = await requestJson<Task>("/api/tasks", {
        method: "POST", signal: controller.signal,
        headers: { "Content-Type": "application/json", "X-Studio-User": userId },
        body: JSON.stringify({ templateId: template.id, assetIds: slots.map((slot) => slot.asset!.id) }),
      });
      if (mounted.current && request.current === controller) {
        store.accept(task);
        queue.clear();
        setValidation({ busy: false, message: "任务已受理，后台正在处理。可以继续上传，创建下一份作品。", error: false });
      }
    } catch (error) {
      if (!mounted.current || request.current !== controller) return;
      const unknown = !(error instanceof ApiError && error.status >= 400 && error.status < 500 && error.status !== 408);
      setUncertain(unknown);
      setValidation({ busy: false, message: unknown ? "未能确认提交结果，任务可能已经受理。请先刷新制作记录核对，避免重复提交。" : error instanceof Error ? error.message : "提交失败", error: true });
      if (unknown) void store.refresh();
      if (error instanceof ApiError && (error.status === 401 || error.code === "SESSION_CHANGED")) void session.refresh();
    } finally {
      clearTimeout(timeout);
      if (request.current === controller) { validating.current = false; request.current = null; }
    }
  }

  return <section aria-labelledby="upload-heading">
    <div className="panel-heading"><h2 id="upload-heading">上传素材</h2><span className="muted">{slots.filter((slot) => slot.status === "READY").length} / {slots.length} 已就绪</span></div>
    <p className="upload-requirements">需要 {template.requiredImageCount} 张 JPEG / PNG，每张不超过 5 MiB。边长 ≤8192 像素，总像素 ≤2000 万。</p>
    <div className="upload-slots">
      {slots.map((slot, index) => <div className={"upload-slot " + slot.status.toLowerCase()} key={index}>
        <div className="slot-topline"><span>素材 {index + 1}</span><span aria-live="polite">{slot.status === "UPLOADING" ? "上传中…" : slot.status === "READY" ? "已上传" : slot.status === "ERROR" ? "需要处理" : "待选择"}</span></div>
        {slot.previewUrl && <div className="upload-preview"><Image fill unoptimized sizes="400px" src={slot.previewUrl} alt={"素材 " + (index + 1) + " 预览"} /></div>}
        <label className={"upload-label" + (disabled ? " disabled" : "")}>
          <span aria-hidden="true">＋ </span>{slot.file ? "替换图片" : "选择图片"}
          <input type="file" accept={template.acceptedMimeTypes.join(",")} aria-label={"选择或替换素材 " + (index + 1)}
            disabled={disabled} aria-invalid={slot.status === "ERROR"} aria-describedby={slot.error ? "upload-error-" + index : undefined} onChange={(event) => {
              const file = event.target.files?.[0]; event.target.value = "";
              if (file) { changed(); void queue.replace(index, file); }
            }} />
        </label>
        {slot.file && <div className="slot-actions">
          <button disabled={disabled} onClick={() => { changed(); queue.remove(index); }} aria-label={"移除素材 " + (index + 1)}>移除</button>
          {slot.status === "ERROR" && <button disabled={disabled} onClick={() => { changed(); queue.retry(index); }}>重试上传</button>}
        </div>}
        {slot.error && <p id={"upload-error-" + index} className="inline-error" role="alert">{slot.error}</p>}
      </div>)}
    </div>
    <button className="primary-button prepare-button" disabled={!ready || disabled || uncertain} onClick={() => { void submit(); }}>
      {validation.busy ? "正在提交…" : "开始制作"}
    </button>
    <p className="muted">提交后可继续浏览，进度与结果会自动更新。刷新或切换身份会清空未提交草稿，已受理任务仍会继续。</p>
    {validation.message && <p className={validation.error ? "inline-error" : "inline-success"} role={validation.error ? "alert" : "status"}>{validation.message}</p>}
    {uncertain && <div className="uncertain-actions"><button onClick={() => { void store.refresh(); }}>刷新制作记录</button><button onClick={() => { setUncertain(false); changed(); }}>已核对记录，允许再次提交</button></div>}
  </section>;
}
