package com.xiaoying.studio.task;

import com.xiaoying.studio.asset.AssetService;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class TaskStageActions {
    private final AssetService assets;
    private final TaskMapper mapper;
    private final TaskResultStorage results;
    public TaskStageActions(AssetService assets, TaskMapper mapper, TaskResultStorage results) {
        this.assets = assets; this.mapper = mapper; this.results = results;
    }
    public String execute(TaskRow task, TaskStage stage) throws IOException {
        return switch (stage) {
            case ASSET_CHECK -> { assets.validateSelection(task.userId(), task.templateId(), mapper.assetIds(task.id())); yield null; }
            case GENERATE -> { results.generate(task.id(), mapper.resultSource(task.templateId())); yield null; }
            case FINALIZE -> results.finalizeResult(task.id());
            default -> throw new IllegalArgumentException("PENDING is not executable");
        };
    }
    public void cleanup(String id) throws IOException { results.cleanTemporary(id); }
}
