package com.xiaoying.studio.task;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskWorkerTest {
    @Test
    void queueFailureStopsAcceptanceAndFurtherAutomaticAttempts() {
        var transitions=mock(TaskTransitions.class);
        var pipeline=mock(TaskPipeline.class);
        var health=new TaskExecutionHealth();
        var worker=new TaskWorker(transitions,pipeline,health,false,500);
        when(transitions.claimNext()).thenThrow(new IllegalStateException("Database unavailable"));
        health.ready();
        worker.runOne();
        worker.runOne();
        assertThat(health.canRun()).isFalse();
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        verify(transitions,times(1)).claimNext();
        verifyNoInteractions(pipeline);
    }
}
