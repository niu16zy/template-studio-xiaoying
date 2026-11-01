package com.xiaoying.studio.task;

import org.springframework.stereotype.Component;

@Component
public class ThreadStageDelay implements StageDelay {
    @Override public void pause(int milliseconds) throws InterruptedException { Thread.sleep(milliseconds); }
}
