package com.xiaoying.studio.task;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskTransitions {
    public static final List<TaskStage> STAGES = List.of(TaskStage.ASSET_CHECK, TaskStage.GENERATE, TaskStage.FINALIZE);
    private final TaskMapper mapper;
    public TaskTransitions(TaskMapper mapper) { this.mapper = mapper; }

    @Transactional
    public TaskRow claimNext() {
        String id = mapper.nextQueued();
        if (id == null || mapper.claim(id) != 1) return null;
        return mapper.find(id);
    }

    @Transactional
    public void begin(String id, TaskStage stage) {
        TaskRow task = running(id);
        List<TaskStepRow> steps = mapper.steps(id);
        int index = STAGES.indexOf(stage);
        if (index < 0 || steps.size() != 3 || steps.get(index).status() != StepStatus.PENDING
                || steps.subList(0, index).stream().anyMatch(step -> step.status() != StepStatus.SUCCEEDED)
                || (index == 0 ? task.currentStage() != TaskStage.PENDING : task.currentStage() != STAGES.get(index - 1))) {
            throw new IllegalStateException("Invalid stage order");
        }
        changed(mapper.stage(id, stage));
        changed(mapper.beginStep(id, stage));
    }

    @Transactional
    public void complete(String id, TaskStage stage, String resultPath) {
        TaskRow task = running(id);
        if (task.currentStage() != stage) throw new IllegalStateException("Mismatched current stage");
        changed(mapper.finishStep(id, stage));
        if (stage == TaskStage.FINALIZE) {
            if (resultPath == null || resultPath.isBlank()) throw new IllegalStateException("Missing result");
            changed(mapper.succeed(id, resultPath));
        } else changed(mapper.touch(id));
    }

    @Transactional
    public void fail(String id, String code, String message) {
        var task = mapper.lock(id);
        if (task == null || task.status() == TaskStatus.SUCCEEDED || task.status() == TaskStatus.FAILED) return;
        // Both writes share one short transaction; final states never regress.
        changed(mapper.fail(id, code, message));
        mapper.stopSteps(id, code, message);
    }

    @Transactional
    public void interruptPreviousRun() {
        for (String id : mapper.unfinished()) {
            fail(id, "SERVICE_RESTARTED", "服务重启导致任务中断，本次不会自动续跑，请重新创建任务");
        }
    }

    private TaskRow running(String id) {
        var task = mapper.lock(id);
        if (task == null || task.status() != TaskStatus.RUNNING) throw new IllegalStateException("Task is not running");
        return task;
    }
    private static void changed(int rows) { if (rows != 1) throw new IllegalStateException("Concurrent task state change"); }
}
