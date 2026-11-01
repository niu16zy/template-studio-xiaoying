package com.xiaoying.studio.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaoying.studio.asset.AssetService;
import com.xiaoying.studio.catalog.CatalogService;
import jakarta.validation.constraints.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskSubmissionService {
    public record Request(@NotBlank String templateId, @NotNull @Size(max=3) List<@NotBlank String> assetIds, JsonNode simulation) {}
    private final TaskMapper mapper;
    private final AssetService assets;
    private final CatalogService catalog;
    private final SimulationSettings simulation;
    private final TaskQueryService queries;
    private final TaskExecutionHealth health;
    public TaskSubmissionService(TaskMapper mapper, AssetService assets, CatalogService catalog,
                                 SimulationSettings simulation, TaskQueryService queries, TaskExecutionHealth health) {
        this.mapper = mapper; this.assets = assets; this.catalog = catalog;
        this.simulation = simulation; this.queries = queries; this.health = health;
    }

    @Transactional
    public TaskView submit(String userId, Request input) {
        health.requireAccepting();
        var template = catalog.template(input.templateId());
        assets.validateSelection(userId, input.templateId(), input.assetIds());
        String frozen = simulation.freeze(input.simulation());
        String id = UUID.randomUUID().toString();
        var now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var row = new TaskRow(id,userId,template.id(),template.title(),TaskStatus.QUEUED,TaskStage.PENDING,
                null,null,null,null,frozen,0,now,null,null,now);
        mapper.insert(row);
        for (int i = 0; i < input.assetIds().size(); i++) mapper.linkAsset(id, input.assetIds().get(i), i);
        for (TaskStage stage : TaskTransitions.STAGES) mapper.insertStep(UUID.randomUUID().toString(), id, stage);
        return queries.view(row);
    }
}
