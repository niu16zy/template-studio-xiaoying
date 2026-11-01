package com.xiaoying.studio.task;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TaskMapper {
    String COLUMNS = "id, user_id, template_id, template_title_snapshot, status, current_stage, failure_stage, error_code, error_message, result_path, simulation_json, version, created_at, started_at, finished_at, updated_at";

    @Insert("""
        INSERT INTO creation_task (id,user_id,template_id,template_title_snapshot,simulation_json,created_at,updated_at)
        VALUES (#{id},#{userId},#{templateId},#{templateTitleSnapshot},#{simulationJson},#{createdAt},#{createdAt})
        """)
    void insert(TaskRow row);

    @Insert("INSERT INTO task_asset (task_id,asset_id,slot_index) VALUES (#{taskId},#{assetId},#{slot})")
    void linkAsset(@Param("taskId") String taskId, @Param("assetId") String assetId, @Param("slot") int slot);

    @Insert("INSERT INTO task_step (id,task_id,stage) VALUES (#{id},#{taskId},#{stage})")
    void insertStep(@Param("id") String id, @Param("taskId") String taskId, @Param("stage") TaskStage stage);

    @Select("SELECT " + COLUMNS + " FROM creation_task WHERE id=#{id}")
    TaskRow find(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM creation_task WHERE id=#{id} AND user_id=#{userId}")
    TaskRow owned(@Param("id") String id, @Param("userId") String userId);

    @Select("SELECT " + COLUMNS + " FROM creation_task WHERE id=#{id} FOR UPDATE")
    TaskRow lock(@Param("id") String id);

    @Select("SELECT id FROM creation_task WHERE status='QUEUED' ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED")
    String nextQueued();

    @Update("""
        UPDATE creation_task SET status='RUNNING',started_at=UTC_TIMESTAMP(6),
        updated_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=#{id} AND status='QUEUED'
        """)
    int claim(@Param("id") String id);

    @Select("""
        SELECT stage,status,started_at,finished_at,error_code,error_message FROM task_step WHERE task_id=#{id}
        ORDER BY FIELD(stage,'ASSET_CHECK','GENERATE','FINALIZE')
        """)
    List<TaskStepRow> steps(@Param("id") String id);

    @Select("SELECT asset_id FROM task_asset WHERE task_id=#{id} ORDER BY slot_index")
    List<String> assetIds(@Param("id") String id);

    @Select("SELECT result_source_path FROM template WHERE id=#{id}")
    String resultSource(@Param("id") String id);

    @Update("""
        UPDATE creation_task SET current_stage=#{stage},version=version+1,updated_at=UTC_TIMESTAMP(6)
        WHERE id=#{id} AND status='RUNNING'
        """)
    int stage(@Param("id") String id, @Param("stage") TaskStage stage);

    @Update("""
        UPDATE task_step SET status='RUNNING',started_at=UTC_TIMESTAMP(6)
        WHERE task_id=#{id} AND stage=#{stage} AND status='PENDING'
        """)
    int beginStep(@Param("id") String id, @Param("stage") TaskStage stage);

    @Update("""
        UPDATE task_step SET status='SUCCEEDED',finished_at=UTC_TIMESTAMP(6)
        WHERE task_id=#{id} AND stage=#{stage} AND status='RUNNING'
        """)
    int finishStep(@Param("id") String id, @Param("stage") TaskStage stage);

    @Update("UPDATE creation_task SET version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND status='RUNNING'")
    int touch(@Param("id") String id);

    @Update("""
        UPDATE creation_task SET status='SUCCEEDED',result_path=#{path},finished_at=UTC_TIMESTAMP(6),
        updated_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=#{id} AND status='RUNNING' AND current_stage='FINALIZE'
        """)
    int succeed(@Param("id") String id, @Param("path") String path);

    @Update("""
        UPDATE creation_task SET status='FAILED',failure_stage=current_stage,error_code=#{code},error_message=#{message},
        finished_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6),version=version+1
        WHERE id=#{id} AND status IN ('QUEUED','RUNNING')
        """)
    int fail(@Param("id") String id, @Param("code") String code, @Param("message") String message);

    @Update("""
        UPDATE task_step SET error_code=CASE WHEN status='RUNNING' THEN #{code} ELSE NULL END,
        error_message=CASE WHEN status='RUNNING' THEN #{message} ELSE NULL END,finished_at=UTC_TIMESTAMP(6)
        ,status=CASE WHEN status='RUNNING' THEN 'FAILED' ELSE 'SKIPPED' END
        WHERE task_id=#{id} AND status IN ('PENDING','RUNNING')
        """)
    int stopSteps(@Param("id") String id, @Param("code") String code, @Param("message") String message);

    @Select("SELECT id FROM creation_task WHERE status IN ('QUEUED','RUNNING') ORDER BY created_at,id")
    List<String> unfinished();

    @Select("SELECT " + COLUMNS + " FROM creation_task WHERE user_id=#{userId} AND status IN ('QUEUED','RUNNING') ORDER BY created_at DESC,id DESC")
    List<TaskRow> active(@Param("userId") String userId);

    @Select("<script>SELECT " + COLUMNS + """
         FROM creation_task WHERE user_id=#{userId}
        <if test="beforeTime != null">
          AND (created_at &lt; #{beforeTime} OR (created_at=#{beforeTime} AND id &lt; #{beforeId}))
        </if>
        ORDER BY created_at DESC,id DESC LIMIT #{count}
        </script>
        """)
    List<TaskRow> history(@Param("userId") String userId, @Param("beforeTime") LocalDateTime beforeTime,
                         @Param("beforeId") String beforeId, @Param("count") int count);
}
