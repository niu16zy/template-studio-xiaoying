package com.xiaoying.studio.task;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;
import com.xiaoying.studio.web.ApiProblem;
import org.springframework.http.HttpStatus;

@Component("taskExecution")
public class TaskExecutionHealth implements HealthIndicator {
    private final AtomicBoolean accepting = new AtomicBoolean(false);
    private final AtomicBoolean faulted = new AtomicBoolean(false);
    public void ready() { accepting.set(true); }
    public void stop() { accepting.set(false); }
    public void fault() { faulted.set(true); accepting.set(false); }
    public boolean canRun() { return accepting.get() && !faulted.get(); }
    public void requireAccepting() {
        if (!canRun()) throw new ApiProblem(HttpStatus.SERVICE_UNAVAILABLE, "WORKER_UNAVAILABLE", "任务服务正在启动、停止或出现故障，请稍后重试");
    }
    @Override public Health health() { return canRun() ? Health.up().build() : Health.down().build(); }
}
