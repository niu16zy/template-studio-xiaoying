package com.xiaoying.studio.task;

import com.fasterxml.jackson.databind.*;
import com.xiaoying.studio.web.ApiProblem;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SimulationSettings {
    public enum FailStage { NONE, ASSET_CHECK, GENERATE, FINALIZE }
    public record Plan(FailStage failStage, Map<TaskStage, Integer> delaysMs) {}
    private final boolean enabled;
    private final Map<TaskStage,Integer> defaults;
    private final ObjectMapper json;

    public SimulationSettings(ObjectMapper json, @Value("${studio.simulation-enabled:true}") boolean enabled,
            @Value("${studio.sim-check-ms:1500}") int check,
            @Value("${studio.sim-gen-ms:6000}") int generate,
            @Value("${studio.sim-finalize-ms:1500}") int finalizeDelay) {
        this.json = json; this.enabled = enabled;
        defaults = Map.of(TaskStage.ASSET_CHECK, check, TaskStage.GENERATE, generate, TaskStage.FINALIZE, finalizeDelay);
        if (defaults.values().stream().anyMatch(n -> n < 0 || n > 30000)) {
            throw new IllegalArgumentException("Simulation delays must be between 0 and 30000 ms");
        }
    }

    public Plan resolve(JsonNode input) {
        var delays = new EnumMap<TaskStage,Integer>(TaskStage.class);
        delays.putAll(defaults);
        FailStage failure = FailStage.NONE;
        if (input != null && !input.isNull()) {
            if (!enabled) throw bad("自定义模拟参数入口已关闭");
            if (!input.isObject()) throw bad("simulation 必须为对象");
            input.fieldNames().forEachRemaining(key -> { if (!Set.of("failStage","delaysMs").contains(key)) throw bad("未知模拟字段：" + key); });
            if (input.has("failStage")) {
                if (!input.get("failStage").isTextual()) throw bad("failStage 必须为阶段名称");
                try { failure = FailStage.valueOf(input.get("failStage").textValue()); }
                catch (IllegalArgumentException exception) { throw bad("不支持的失败阶段"); }
            }
            if (input.has("delaysMs")) {
                var node = input.get("delaysMs");
                if (!node.isObject()) throw bad("delaysMs 必须为对象");
                node.fields().forEachRemaining(entry -> {
                    TaskStage stage;
                    try { stage = TaskStage.valueOf(entry.getKey()); }
                    catch (IllegalArgumentException exception) { throw bad("未知耗时阶段"); }
                    var value = entry.getValue();
                    if (stage == TaskStage.PENDING || !value.isIntegralNumber() || !value.canConvertToInt()
                            || value.intValue() < 0 || value.intValue() > 30000) {
                        throw bad("每阶段耗时必须为 0～30000 的整数毫秒");
                    }
                    delays.put(stage, value.intValue());
                });
            }
        }
        return new Plan(failure, Collections.unmodifiableMap(delays));
    }

    public String freeze(JsonNode input) {
        try { return json.writeValueAsString(resolve(input)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
    public Plan read(String snapshot) {
        try { return json.readValue(snapshot, Plan.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException("Invalid stored simulation", exception); }
    }
    private static ApiProblem bad(String message) { return new ApiProblem(HttpStatus.BAD_REQUEST, "INVALID_SIMULATION", message); }
}
