package com.xiaoying.studio.task;
import java.time.Instant;
import java.util.List;

public record TaskView(String id, String templateId, String templateTitle, TaskStatus status,
        TaskStage currentStage, List<Step> steps, String resultUrl, TaskStage failureStage,
        String errorCode, String errorMessage, Instant createdAt, Instant startedAt,
        Instant finishedAt, long version) {
    public record Step(TaskStage stage, StepStatus status, Instant startedAt, Instant finishedAt,
                       String errorCode, String errorMessage) {}
    public record Page(List<TaskView> items, String nextCursor) {}
}
