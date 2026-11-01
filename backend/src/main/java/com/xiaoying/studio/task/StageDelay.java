package com.xiaoying.studio.task;

@FunctionalInterface
public interface StageDelay {
    void pause(int milliseconds) throws InterruptedException;
}
