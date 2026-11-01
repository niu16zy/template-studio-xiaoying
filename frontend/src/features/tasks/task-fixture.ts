import type { Task } from "@/lib/contracts";

export function taskFixture(id: string, patch: Partial<Task> = {}): Task {
  return { id, templateId: "one", templateTitle: "旅行明信片", status: "QUEUED", currentStage: "PENDING", version: 0,
    resultUrl: null, failureStage: null, errorCode: null, errorMessage: null, createdAt: "2026-10-31T12:00:00Z", startedAt: null, finishedAt: null,
    steps: ["ASSET_CHECK", "GENERATE", "FINALIZE"].map((stage) => ({ stage: stage as "ASSET_CHECK" | "GENERATE" | "FINALIZE", status: "PENDING", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null })), ...patch };
}
