export interface Category {
  id: string;
  name: string;
  sortOrder: number;
}

export interface Template {
  id: string;
  categoryId: string;
  title: string;
  description: string;
  coverUrl: string;
  sampleUrl: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors: Record<string, string>;
  requestId: string;
}

export interface DemoUser { id: string; displayName: string }
export interface Asset { id: string; previewUrl: string; mimeType: string; sizeBytes: number; width: number; height: number }

export type TaskStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type TaskStage = "PENDING" | "ASSET_CHECK" | "GENERATE" | "FINALIZE";
export type StepStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";

export interface TaskStep {
  stage: Exclude<TaskStage, "PENDING">;
  status: StepStatus;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}
export interface Task {
  id: string;
  templateId: string;
  templateTitle: string;
  status: TaskStatus;
  currentStage: TaskStage;
  steps: TaskStep[];
  resultUrl: string | null;
  failureStage: TaskStage | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  version: number;
}
export interface TaskPage { items: Task[]; nextCursor: string | null }
