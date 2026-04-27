package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.AdminSessionDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 后台管理员会话 Mapper
 */
@Mapper
public interface AdminSessionMapper {

    @Results(id = "adminSessionResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "sessionTokenHash", column = "session_token_hash"),
            @Result(property = "expireTime", column = "expire_time"),
            @Result(property = "lastAccessTime", column = "last_access_time"),
            @Result(property = "revoked", column = "revoked"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time")
    })
    @Select("""
            SELECT id, user_id, session_token_hash, expire_time, last_access_time, revoked, create_time, update_time
            FROM admin_session
            WHERE session_token_hash = #{sessionTokenHash}
              AND revoked = 0
              AND expire_time > NOW()
            LIMIT 1
            """)
    AdminSessionDO selectActiveByTokenHash(@Param("sessionTokenHash") String sessionTokenHash);

    @Insert("""
            INSERT INTO admin_session (
                user_id, session_token_hash, expire_time, last_access_time, revoked, create_time, update_time
            ) VALUES (
                #{userId}, #{sessionTokenHash}, #{expireTime}, #{lastAccessTime}, #{revoked}, #{createTime}, #{updateTime}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminSessionDO record);

    @Update("""
            UPDATE admin_session
            SET revoked = 1,
                update_time = NOW()
            WHERE session_token_hash = #{sessionTokenHash}
              AND revoked = 0
            """)
    int revokeByTokenHash(@Param("sessionTokenHash") String sessionTokenHash);

    @Update("""
            UPDATE admin_session
            SET revoked = 1,
                update_time = NOW()
            WHERE user_id = #{userId}
              AND revoked = 0
            """)
    int revokeByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE admin_session
            SET revoked = 1,
                update_time = NOW()
            WHERE revoked = 0
              AND expire_time <= NOW()
            """)
    int revokeExpiredSessions();

    @Update("""
            UPDATE admin_session
            SET last_access_time = #{lastAccessTime},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND revoked = 0
            """)
    int updateLastAccessTime(@Param("id") Long id,
                             @Param("lastAccessTime") LocalDateTime lastAccessTime,
                             @Param("updateTime") LocalDateTime updateTime);
}
