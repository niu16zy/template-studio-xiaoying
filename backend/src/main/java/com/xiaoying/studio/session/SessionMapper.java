package com.xiaoying.studio.session;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SessionMapper {
    @Select("SELECT id, display_name FROM demo_user ORDER BY id")
    List<DemoUser> users();

    @Select("SELECT id, display_name FROM demo_user WHERE id = #{id}")
    DemoUser user(@Param("id") String id);

    @Select("""
        SELECT u.id, u.display_name FROM demo_user u JOIN user_session s ON s.user_id = u.id
        WHERE s.token_hash = #{hash} AND s.expires_at > #{now}
        """)
    DemoUser current(@Param("hash") String hash, @Param("now") LocalDateTime now);

    @Insert("""
        INSERT INTO user_session (id, token_hash, user_id, expires_at)
        VALUES (#{id}, #{hash}, #{userId}, #{expiresAt})
        """)
    void insert(@Param("id") String id, @Param("hash") String hash,
                @Param("userId") String userId, @Param("expiresAt") LocalDateTime expiresAt);

    @Delete("DELETE FROM user_session WHERE token_hash = #{hash}")
    void delete(@Param("hash") String hash);
}
