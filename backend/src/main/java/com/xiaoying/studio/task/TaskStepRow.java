package com.xiaoying.studio.task;
import java.time.LocalDateTime;
public record TaskStepRow(TaskStage stage, StepStatus status, LocalDateTime startedAt,
                          LocalDateTime finishedAt, String errorCode, String errorMessage) {}
