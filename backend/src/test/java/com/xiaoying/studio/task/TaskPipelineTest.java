package com.xiaoying.studio.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskPipelineTest {
    private final TaskTransitions transitions = mock(TaskTransitions.class);
    private final TaskStageActions actions = mock(TaskStageActions.class);
    private final StageDelay delay = mock(StageDelay.class);
    private final SimulationSettings settings = new SimulationSettings(new ObjectMapper(), true, 0, 0, 0);
    private final TaskPipeline pipeline = new TaskPipeline(transitions, actions, settings, delay);
    private final String id = "00000000-0000-4000-8000-000000000099";

    private TaskRow task(SimulationSettings.FailStage failure) throws Exception {
        var input = new ObjectMapper().readTree("{\"failStage\":\"" + failure + "\"}");
        var now = LocalDateTime.now();
        return new TaskRow(id,"user","template","title",TaskStatus.RUNNING,TaskStage.PENDING,null,null,null,
                null,settings.freeze(input),1,now,now,null,now);
    }
    @Test
    void executesEveryBoundaryInOrderAndPublishesOnlyAfterFinalization() throws Exception {
        TaskRow row = task(SimulationSettings.FailStage.NONE);
        when(actions.execute(row, TaskStage.FINALIZE)).thenReturn("results/result.png");
        pipeline.execute(row);
        var order = inOrder(transitions, actions, delay);
        for (TaskStage stage : TaskTransitions.STAGES) {
            order.verify(transitions).begin(id, stage);
            order.verify(delay).pause(0);
            order.verify(actions).execute(row, stage);
            order.verify(transitions).complete(id, stage, stage == TaskStage.FINALIZE ? "results/result.png" : null);
        }
        order.verify(actions).cleanup(id);
        verify(transitions, never()).fail(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value=SimulationSettings.FailStage.class, names={"ASSET_CHECK","GENERATE","FINALIZE"})
    void injectedFailureStopsTheCurrentActionAndEverySubsequentStage(SimulationSettings.FailStage failure) throws Exception {
        var row = task(failure);
        pipeline.execute(row);
        TaskStage failed = TaskStage.valueOf(failure.name());
        int index = TaskTransitions.STAGES.indexOf(failed);
        verify(transitions).begin(id, failed);
        verify(transitions).fail(eq(id), eq("SIMULATED_FAILURE"), contains("后续步骤未执行"));
        for (int i = 0; i < 3; i++) {
            var stage = TaskTransitions.STAGES.get(i);
            if (i < index) verify(transitions).complete(id, stage, null);
            else {
                verify(actions, never()).execute(row, stage);
                verify(transitions, never()).complete(eq(id), eq(stage), any());
                if (i > index) verify(transitions, never()).begin(id, stage);
            }
        }
        verify(actions).cleanup(id);
    }

    @Test
    void realIoFailureAlsoStopsFinalization() throws Exception {
        var row = task(SimulationSettings.FailStage.NONE);
        when(actions.execute(row,TaskStage.GENERATE)).thenThrow(new IOException("private disk path"));
        pipeline.execute(row);
        verify(transitions).fail(eq(id),eq("RESULT_IO_ERROR"),argThat(message -> !message.contains("private disk path")));
        verify(actions,never()).execute(row,TaskStage.FINALIZE);
    }

    @Test
    void interruptionIsPersistedAndRestoresInterruptFlag() throws Exception {
        doThrow(new InterruptedException()).when(delay).pause(anyInt());
        try {
            pipeline.execute(task(SimulationSettings.FailStage.NONE));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(transitions).fail(eq(id),eq("WORKER_INTERRUPTED"),anyString());
        } finally { Thread.interrupted(); }
    }

    @Test
    void inabilityToPersistFailureEscapesToTheWorker() throws Exception {
        doThrow(new IllegalStateException("DB down")).when(transitions).fail(anyString(),anyString(),anyString());
        assertThatThrownBy(() -> pipeline.execute(task(SimulationSettings.FailStage.ASSET_CHECK))).isInstanceOf(IllegalStateException.class);
        verify(actions).cleanup(id);
    }
}
