package com.xiaoying.studio.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.web.ApiProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SimulationSettingsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SimulationSettings settings = new SimulationSettings(json,true,1500,6000,1500);
    @Test
    void fillsDefaultsAndFreezesAnIndependentSnapshot() throws Exception {
        String frozen = settings.freeze(json.readTree("{\"failStage\":\"FINALIZE\",\"delaysMs\":{\"GENERATE\":12000}}"));
        var changedDefaults = new SimulationSettings(json,true,1,2,3);
        var restored = changedDefaults.read(frozen);
        assertThat(restored.delaysMs().get(TaskStage.ASSET_CHECK)).isEqualTo(1500);
        assertThat(restored.delaysMs().get(TaskStage.GENERATE)).isEqualTo(12000);
        assertThat(restored.failStage()).isEqualTo(SimulationSettings.FailStage.FINALIZE);
    }
    @ParameterizedTest
    @ValueSource(strings={
        "[]", "{\"failStage\":\"PENDING\"}", "{\"failStage\":null}", "{\"extra\":1}",
        "{\"delaysMs\":{\"GENERATE\":-1}}", "{\"delaysMs\":{\"GENERATE\":30001}}",
        "{\"delaysMs\":{\"GENERATE\":1.5}}", "{\"delaysMs\":{\"GENERATE\":\"2\"}}",
        "{\"delaysMs\":{\"PENDING\":1}}", "{\"delaysMs\":{\"UNKNOWN\":1}}"
    })
    void rejectsInvalidSimulationInsteadOfCoercingIt(String value) throws Exception {
        var input = json.readTree(value);
        assertThatThrownBy(() -> settings.resolve(input)).isInstanceOf(ApiProblem.class);
    }
    @Test
    void disabledOverridesStillAllowDefaultSimulation() throws Exception {
        var disabled = new SimulationSettings(json,false,1,2,3);
        assertThat(disabled.resolve(null).failStage()).isEqualTo(SimulationSettings.FailStage.NONE);
        var input = json.readTree("{}");
        assertThatThrownBy(() -> disabled.resolve(input)).isInstanceOf(ApiProblem.class);
    }
}
