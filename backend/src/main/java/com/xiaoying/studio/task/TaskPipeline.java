package com.xiaoying.studio.task;

import com.xiaoying.studio.web.*;
import java.io.IOException;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class TaskPipeline {
    private static final Logger log = LoggerFactory.getLogger(TaskPipeline.class);
    private final TaskTransitions transitions;
    private final TaskStageActions actions;
    private final SimulationSettings simulation;
    private final StageDelay delay;
    public TaskPipeline(TaskTransitions transitions, TaskStageActions actions, SimulationSettings simulation, StageDelay delay) {
        this.transitions = transitions; this.actions = actions; this.simulation = simulation; this.delay = delay;
    }

    // Intentionally not transactional: delays and file operations never hold a database transaction.
    public void execute(TaskRow task) {
        TaskStage stage = TaskStage.PENDING;
        try {
            var plan = simulation.read(task.simulationJson());
            for (TaskStage next : TaskTransitions.STAGES) {
                stage = next;
                transitions.begin(task.id(), stage);
                delay.pause(plan.delaysMs().get(stage));
                if (plan.failStage().name().equals(stage.name())) {
                    throw new InjectedFailure("按演示设置在" + label(stage) + "阶段失败，后续步骤未执行");
                }
                String result = actions.execute(task, stage);
                transitions.complete(task.id(), stage, result);
            }
        } catch (InterruptedException exception) {
            try { transitions.fail(task.id(), "WORKER_INTERRUPTED", "任务执行线程停止，当前任务已中断"); }
            finally { Thread.currentThread().interrupt(); }
        } catch (Exception exception) {
            log.warn("Task {} failed at {}", task.id(), stage, exception);
            String code;
            String message;
            if (exception instanceof InjectedFailure) { code = "SIMULATED_FAILURE"; message = exception.getMessage(); }
            else if (exception instanceof ApiProblem problem) { code = problem.code(); message = problem.getMessage(); }
            else if (exception instanceof ResourceNotFoundException) { code = "ASSET_UNAVAILABLE"; message = exception.getMessage(); }
            else if (exception instanceof IOException) { code = "RESULT_IO_ERROR"; message = label(stage) + "阶段文件处理失败，请重新创建任务"; }
            else { code = "EXECUTION_ERROR"; message = label(stage) + "阶段执行异常，请重新创建任务"; }
            // If this write also fails, let the worker stop and report unhealthy. Do not claim a persisted failure.
            transitions.fail(task.id(), code, message);
        } finally {
            try { actions.cleanup(task.id()); }
            catch (IOException exception) { log.warn("Could not clean temporary result for {}", task.id(), exception); }
        }
    }

    private static String label(TaskStage stage) {
        return switch (stage) { case ASSET_CHECK -> "素材检查"; case GENERATE -> "模拟生成"; case FINALIZE -> "结果整理"; default -> "准备"; };
    }
    private static class InjectedFailure extends RuntimeException {
        InjectedFailure(String message) { super(message); }
    }
}
