import { ApiError, requestJson } from "@/lib/api";
import type { Asset, Template } from "@/lib/contracts";

export type UploadSlot = {
  version: number;
  status: "EMPTY" | "UPLOADING" | "READY" | "ERROR";
  previewUrl?: string;
  file?: File;
  asset?: Asset;
  error?: string;
};
export function validateFile(file: File, template: Template): string | null {
  if (!template.acceptedMimeTypes.includes(file.type)) return "请选择 JPEG 或 PNG 图片";
  if (file.size === 0) return "图片为空，请重新选择";
  if (file.size > template.maxFileSizeBytes) return "单张图片不能超过 5 MiB";
  return null;
}

// Each slot owns a sequence, request and object URL. Abort alone cannot prevent stale responses.
export class UploadQueue {
  private slots: UploadSlot[];
  private listeners = new Set<() => void>();
  private requests = new Map<number, AbortController>();
  private timers = new Map<number, ReturnType<typeof setTimeout>>();
  constructor(private template: Template, private userId: string, private onSessionError: () => void) {
    this.slots = Array.from({ length: template.requiredImageCount }, () => ({ version: 0, status: "EMPTY" }));
  }
  getSnapshot = () => this.slots;
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  private update(index: number, value: UploadSlot) {
    this.slots = this.slots.map((slot, i) => i === index ? value : slot);
    this.listeners.forEach((listener) => listener());
  }
  remove = (index: number) => {
    this.requests.get(index)?.abort();
    this.requests.delete(index);
    clearTimeout(this.timers.get(index));
    this.timers.delete(index);
    const slot = this.slots[index];
    if (slot.previewUrl) URL.revokeObjectURL(slot.previewUrl);
    this.update(index, { version: slot.version + 1, status: "EMPTY" });
  };
  clear = () => { this.slots.forEach((_, index) => this.remove(index)); };
  retry = (index: number) => { const file = this.slots[index].file; if (file) void this.replace(index, file); };

  replace = async (index: number, file: File) => {
    this.remove(index);
    const version = this.slots[index].version;
    const invalid = validateFile(file, this.template);
    if (invalid) { this.update(index, { version, status: "ERROR", error: invalid }); return; }
    const previewUrl = URL.createObjectURL(file);
    const controller = new AbortController();
    this.requests.set(index, controller);
    this.update(index, { version, status: "UPLOADING", previewUrl, file });
    const timeout = setTimeout(() => controller.abort(), 30_000);
    this.timers.set(index, timeout);
    const body = new FormData();
    body.set("templateId", this.template.id);
    body.set("file", file);
    try {
      const asset = await requestJson<Asset>("/api/assets", {
        method: "POST", body, signal: controller.signal, headers: { "X-Studio-User": this.userId },
      });
      if (this.slots[index].version !== version) return;
      this.update(index, { version, status: "READY", previewUrl, file, asset });
    } catch (error) {
      if (this.slots[index].version !== version) return;
      this.update(index, { version, status: "ERROR", previewUrl, file,
        error: controller.signal.aborted ? "上传超时，请重试" : error instanceof Error ? error.message : "上传失败，请重试" });
      if (error instanceof ApiError && (error.status === 401 || error.code === "SESSION_CHANGED")) this.onSessionError();
    } finally {
      clearTimeout(timeout);
      if (this.requests.get(index) === controller) { this.requests.delete(index); this.timers.delete(index); }
    }
  };
}
