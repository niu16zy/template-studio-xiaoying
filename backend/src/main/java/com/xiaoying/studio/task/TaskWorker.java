package com.xiaoying.studio.task;

import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;

@Component
public class TaskWorker implements ApplicationRunner, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);
    private final TaskTransitions transitions;
    private final TaskPipeline pipeline;
    private final TaskExecutionHealth health;
    private final boolean enabled;
    private final long pollMillis;
    private ScheduledExecutorService executor;

    public TaskWorker(TaskTransitions transitions, TaskPipeline pipeline, TaskExecutionHealth health,
            @Value("${studio.worker-enabled:true}") boolean enabled,
            @Value("${studio.queue-poll-ms:500}") long pollMillis) {
        if (pollMillis < 50 || pollMillis > 10000) throw new IllegalArgumentException("Queue poll must be 50..10000 ms");
        this.transitions = transitions; this.pipeline = pipeline; this.health = health;
        this.enabled = enabled; this.pollMillis = pollMillis;
    }
    @Override public void run(ApplicationArguments args) {
        transitions.interruptPreviousRun();
        health.ready();
        if (enabled) {
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                var thread = new Thread(r, "material-task-worker");
                thread.setDaemon(false);
                return thread;
            });
            executor.scheduleWithFixedDelay(this::runOne, 0, pollMillis, TimeUnit.MILLISECONDS);
        }
    }

    // Also used by controlled integration tests with automatic polling disabled.
    public void runOne() {
        if (!health.canRun()) return;
        try {
            TaskRow task = transitions.claimNext();
            if (task != null) pipeline.execute(task);
        } catch (Exception exception) {
            health.fault();
            log.error("Task worker stopped after a persistence/queue failure; restart after repairing the dependency", exception);
        }
    }
    @Override public void destroy() throws InterruptedException {
        health.stop();
        if (executor != null) {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) log.warn("Task worker did not stop within 10 seconds");
        }
    }
}
