package com.xiaoying.studio.task;

import com.xiaoying.studio.session.SessionService;
import com.xiaoying.studio.web.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final SessionService sessions;
    private final TaskSubmissionService submissions;
    private final TaskQueryService queries;
    private final TaskResultStorage results;
    public TaskController(SessionService sessions, TaskSubmissionService submissions, TaskQueryService queries, TaskResultStorage results) {
        this.sessions = sessions; this.submissions = submissions; this.queries = queries; this.results = results;
    }

    @PostMapping
    public ResponseEntity<TaskView> submit(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
            @RequestHeader("X-Studio-User") String expectedUser, @Valid @RequestBody TaskSubmissionService.Request input) {
        var user = sessions.requireExpectedUser(token, expectedUser);
        var task = submissions.submit(user.id(), input);
        return ResponseEntity.accepted().location(URI.create("/api/tasks/" + task.id())).body(task);
    }
    @GetMapping("/{id}")
    public TaskView task(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token, @PathVariable String id) {
        return queries.get(sessions.requireUser(token).id(), id);
    }
    @GetMapping
    public TaskView.Page history(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return queries.history(sessions.requireUser(token).id(), cursor, limit);
    }
    @GetMapping("/active")
    public java.util.List<TaskView> active(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
        return queries.active(sessions.requireUser(token).id());
    }
    @GetMapping("/{id}/result")
    public ResponseEntity<Resource> result(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token, @PathVariable String id) {
        var task = queries.owned(sessions.requireUser(token).id(), id);
        if (task.status() != TaskStatus.SUCCEEDED) throw new ApiProblem(HttpStatus.CONFLICT, "RESULT_NOT_READY", "当前任务没有可预览的成功结果");
        try {
            var path = results.readable(task.resultPath());
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(java.nio.file.Files.size(path))
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + task.id() + ".png\"")
                    .body(new FileSystemResource(path));
        } catch (IOException exception) {
            throw new ResourceNotFoundException("结果文件暂不可用，任务历史仍保留");
        }
    }
}
