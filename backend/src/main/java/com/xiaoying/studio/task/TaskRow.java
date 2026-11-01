package com.xiaoying.studio.task;

import java.time.LocalDateTime;

public record TaskRow(String id, String userId, String templateId, String templateTitleSnapshot,
        TaskStatus status, TaskStage currentStage, TaskStage failureStage, String errorCode,
        String errorMessage, String resultPath, String simulationJson, long version,
        LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime updatedAt) {}
