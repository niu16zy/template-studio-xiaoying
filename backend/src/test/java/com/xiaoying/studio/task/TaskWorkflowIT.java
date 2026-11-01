package com.xiaoying.studio.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.asset.AssetService;
import com.xiaoying.studio.session.SessionService;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TaskWorkflowIT.DelayConfiguration.class)
class TaskWorkflowIT {
    static final String A="00000000-0000-4000-8000-000000000001", B="00000000-0000-4000-8000-000000000002";
    static final Path STORAGE = temporary();
    @Autowired TaskSubmissionService submissions;
    @Autowired TaskQueryService queries;
    @Autowired TaskMapper mapper;
    @Autowired TaskTransitions transitions;
    @Autowired TaskWorker worker;
    @Autowired TaskExecutionHealth health;
    @Autowired AssetService assets;
    @Autowired SessionService sessions;
    @Autowired BlockingDelay delay;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String assetId;
    Cookie cookie;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("studio.storage-root", () -> STORAGE.toString());
    }
    static Path temporary() {
        try { return Files.createTempDirectory("task-workflow-it-"); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @TestConfiguration static class DelayConfiguration {
        @Bean @Primary BlockingDelay controlledDelay() { return new BlockingDelay(); }
    }
    static class BlockingDelay implements StageDelay {
        volatile CountDownLatch entered = new CountDownLatch(1);
        volatile CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean block = new AtomicBoolean();
        void reset() { entered=new CountDownLatch(1); release=new CountDownLatch(1); block.set(false); }
        public void pause(int ms) throws InterruptedException {
            if (block.compareAndSet(true,false)) {
                entered.countDown();
                if (!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test did not release worker");
            }
        }
    }
    @BeforeEach void prepare() throws Exception {
        clearTasks();
        health.ready();
        delay.reset();
        var file = new MockMultipartFile("file","image.jpg","image/jpeg",new ClassPathResource("demo/coast-cover.jpg").getInputStream());
        assetId = assets.upload(A,"afternoon-light",file).id();
        cookie = new Cookie(SessionService.COOKIE_NAME,sessions.switchUser(A,null).token());
    }
    @AfterEach void cleanup() {
        delay.release.countDown();
        clearTasks();
    }
    void clearTasks() {
        jdbc.update("DELETE FROM task_step");
        jdbc.update("DELETE FROM task_asset");
        jdbc.update("DELETE FROM creation_task");
    }
    TaskView submit(String fail) throws Exception {
        return submissions.submit(A,new TaskSubmissionService.Request("afternoon-light",List.of(assetId),
                json.readTree("{\"failStage\":\""+fail+"\",\"delaysMs\":{\"GENERATE\":20000}}")));
    }

    @Test
    void httpAcceptanceReturnsBeforeExecutionAndReadsCannotAdvanceTheTask() throws Exception {
        String body=json.writeValueAsString(Map.of("templateId","afternoon-light","assetIds",List.of(assetId)));
        var accepted=mvc.perform(post("/api/tasks").cookie(cookie).header("X-Studio-Request","1").header("X-Studio-User",A)
                .contentType("application/json").content(body)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED")).andReturn();
        String id=json.readTree(accepted.getResponse().getContentAsString()).get("id").asText();
        assertThat(accepted.getResponse().getHeader("Location")).isEqualTo("/api/tasks/"+id);
        for(int i=0;i<3;i++) {
            var snapshot=queries.get(A,id);
            assertThat(snapshot.status()).isEqualTo(TaskStatus.QUEUED);
            assertThat(snapshot.version()).isZero();
        }
        delay.block.set(true);
        var executor=Executors.newSingleThreadExecutor();
        try {
            Future<?> execution=executor.submit(worker::runOne);
            assertThat(delay.entered.await(3,TimeUnit.SECONDS)).isTrue();
            var processing=queries.get(A,id);
            assertThat(processing.currentStage()).isEqualTo(TaskStage.ASSET_CHECK);
            assertThat(processing.steps().getFirst().status()).isEqualTo(StepStatus.RUNNING);
            assertThat(execution.isDone()).isFalse();
            delay.release.countDown();
            execution.get(5,TimeUnit.SECONDS);
        } finally { delay.release.countDown(); executor.shutdownNow(); executor.awaitTermination(5,TimeUnit.SECONDS); }
        // It completed without any reads after the release; querying only observes persisted work.
        var result=queries.get(A,id);
        assertThat(result.status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(result.steps()).allSatisfy(step -> {
            assertThat(step.status()).isEqualTo(StepStatus.SUCCEEDED);
            assertThat(step.startedAt()).isNotNull();
            assertThat(step.finishedAt()).isAfterOrEqualTo(step.startedAt());
        });
        for(int i=1;i<3;i++) assertThat(result.steps().get(i).startedAt()).isAfterOrEqualTo(result.steps().get(i-1).finishedAt());
        mvc.perform(get(result.resultUrl()).cookie(cookie)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        assertThat(transitions.claimNext()).isNull();
        transitions.fail(id,"SHOULD_NOT_APPLY","late callback");
        assertThat(queries.get(A,id).version()).isEqualTo(result.version());
    }

    @Test
    void multipleTasksPersistEachFailureBoundaryAndHistoryIsOwnedAndPaginated() throws Exception {
        var ids=new ArrayList<String>();
        for(String fail: List.of("ASSET_CHECK","GENERATE","FINALIZE","NONE")) ids.add(submit(fail).id());
        assertThat(queries.active(A)).hasSize(4);
        for(int i=0;i<4;i++) worker.runOne();
        for(int i=0;i<3;i++) {
            var task=queries.get(A,ids.get(i));
            assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(task.failureStage()).isEqualTo(TaskTransitions.STAGES.get(i));
            assertThat(task.errorCode()).isEqualTo("SIMULATED_FAILURE");
            assertThat(task.resultUrl()).isNull();
            for(int step=0;step<3;step++) {
                assertThat(task.steps().get(step).status()).isEqualTo(step<i ? StepStatus.SUCCEEDED : step==i ? StepStatus.FAILED : StepStatus.SKIPPED);
            }
            assertThat(task.steps().get(i).errorMessage()).contains("后续步骤未执行");
        }
        assertThat(queries.get(A,ids.getLast()).status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(queries.active(A)).isEmpty();
        assertThat(queries.history(B,null,20).items()).isEmpty();
        var first=queries.history(A,null,2);
        var second=queries.history(A,first.nextCursor(),2);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2);
        assertThat(second.nextCursor()).isNull();
        var all=new HashSet<String>();
        first.items().forEach(t->all.add(t.id()));
        second.items().forEach(t->all.add(t.id()));
        assertThat(all).containsExactlyInAnyOrderElementsOf(ids);
        Cookie userB=new Cookie(SessionService.COOKIE_NAME,sessions.switchUser(B,null).token());
        mvc.perform(get("/api/tasks/"+ids.getLast()).cookie(userB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks/"+ids.getLast()+"/result").cookie(userB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").cookie(cookie).param("cursor","bad-cursor")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks").cookie(cookie).param("limit","no-number")).andExpect(status().isBadRequest());
    }

    @Test
    void restartClosesQueuedAndRunningButPreservesFinalResults() throws Exception {
        String completed=submit("NONE").id();
        worker.runOne();
        var before=queries.get(A,completed);
        String running=submit("NONE").id();
        assertThat(transitions.claimNext().id()).isEqualTo(running);
        transitions.begin(running,TaskStage.ASSET_CHECK);
        String queued=submit("NONE").id();
        transitions.interruptPreviousRun();
        assertThat(queries.get(A,completed)).isEqualTo(before);
        for(String id:List.of(running,queued)) {
            assertThat(queries.get(A,id).status()).isEqualTo(TaskStatus.FAILED);
            assertThat(queries.get(A,id).errorCode()).isEqualTo("SERVICE_RESTARTED");
        }
        assertThat(queries.get(A,running).steps().getFirst().status()).isEqualTo(StepStatus.FAILED);
        assertThat(queries.get(A,queued).steps()).allSatisfy(s->assertThat(s.status()).isEqualTo(StepStatus.SKIPPED));
        assertThat(queries.get(A,queued).failureStage()).isEqualTo(TaskStage.PENDING);
        mvc.perform(get(before.resultUrl()).cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void submissionRejectsInvalidInputsWithoutCreatingAnyRecords() throws Exception {
        for(var body:List.of(
            Map.of("templateId","afternoon-light","assetIds",List.of()),
            Map.of("templateId","afternoon-light","assetIds",List.of(assetId),"simulation",Map.of("delaysMs",Map.of("GENERATE",1.5))),
            Map.of("templateId","missing","assetIds",List.of(assetId)))) {
            mvc.perform(post("/api/tasks").cookie(cookie).header("X-Studio-Request","1").header("X-Studio-User",A)
                    .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().is4xxClientError());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creation_task",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_step",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_asset",Integer.class)).isZero();
    }

    @Test
    void lostInputAfterAcceptanceFailsAtAssetCheckWithoutGeneratingAResult() throws Exception {
        String id=submit("NONE").id();
        String relative=jdbc.queryForObject("SELECT storage_path FROM asset WHERE id=?",String.class,assetId);
        Files.delete(STORAGE.resolve(relative));
        worker.runOne();
        var task=queries.get(A,id);
        assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.failureStage()).isEqualTo(TaskStage.ASSET_CHECK);
        assertThat(task.errorCode()).isEqualTo("ASSET_UNAVAILABLE");
        assertThat(task.steps().stream().map(TaskView.Step::status).toList())
                .containsExactly(StepStatus.FAILED,StepStatus.SKIPPED,StepStatus.SKIPPED);
        assertThat(task.resultUrl()).isNull();
    }
}
