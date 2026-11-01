package com.xiaoying.studio.asset;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AssetMapper {
    @Insert("""
        INSERT INTO asset (id, user_id, template_id, storage_path, original_name, mime_type, size_bytes, width, height)
        VALUES (#{id}, #{userId}, #{templateId}, #{storagePath}, #{originalName}, #{mimeType}, #{sizeBytes}, #{width}, #{height})
        """)
    void insert(AssetRow asset);

    @Select("""
        SELECT id, user_id, template_id, storage_path, original_name, mime_type, size_bytes, width, height
        FROM asset WHERE id = #{id} AND user_id = #{userId}
        """)
    AssetRow owned(@Param("id") String id, @Param("userId") String userId);
}
