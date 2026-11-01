package com.xiaoying.studio.task;

import com.xiaoying.studio.web.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class TaskQueryService {
    private final TaskMapper mapper;
    public TaskQueryService(TaskMapper mapper) { this.mapper = mapper; }
    public TaskRow owned(String userId, String id) {
        var task = mapper.owned(id, userId);
        if (task == null) throw new ResourceNotFoundException("任务不存在或不属于当前身份");
        return task;
    }
    public TaskView get(String userId, String id) { return view(owned(userId, id)); }
    public List<TaskView> active(String userId) { return mapper.active(userId).stream().map(this::view).toList(); }
    public TaskView.Page history(String userId, String cursor, int limit) {
        if (limit < 1 || limit > 50) throw badCursor("limit 必须在 1～50 之间");
        LocalDateTime time = null;
        String id = null;
        if (cursor != null) {
            try {
                if (cursor.length() > 200) throw new IllegalArgumentException();
                var parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
                if (parts.length != 2) throw new IllegalArgumentException();
                time = LocalDateTime.parse(parts[0]);
                if (time.getYear() < 1000 || time.getYear() > 9999) throw new IllegalArgumentException();
                id = UUID.fromString(parts[1]).toString();
                if (!id.equals(parts[1])) throw new IllegalArgumentException();
            } catch (RuntimeException exception) { throw badCursor("历史分页游标无效"); }
        }
        var rows = mapper.history(userId, time, id, limit + 1);
        boolean more = rows.size() > limit;
        var page = rows.subList(0, Math.min(limit, rows.size()));
        String next = more ? cursor(page.getLast()) : null;
        return new TaskView.Page(page.stream().map(this::view).toList(), next);
    }
    public TaskView view(TaskRow row) {
        var steps = mapper.steps(row.id()).stream().map(step -> new TaskView.Step(step.stage(), step.status(),
                instant(step.startedAt()), instant(step.finishedAt()), step.errorCode(), step.errorMessage())).toList();
        return new TaskView(row.id(), row.templateId(), row.templateTitleSnapshot(), row.status(), row.currentStage(), steps,
                row.status() == TaskStatus.SUCCEEDED ? "/api/tasks/" + row.id() + "/result" : null,
                row.failureStage(), row.errorCode(), row.errorMessage(), instant(row.createdAt()),
                instant(row.startedAt()), instant(row.finishedAt()), row.version());
    }
    private static String cursor(TaskRow row) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((row.createdAt() + "|" + row.id()).getBytes(StandardCharsets.UTF_8));
    }
    private static Instant instant(LocalDateTime time) { return time == null ? null : time.toInstant(ZoneOffset.UTC); }
    private static ApiProblem badCursor(String message) { return new ApiProblem(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", message); }
}
