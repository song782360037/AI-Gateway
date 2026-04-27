package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.AdminUserDO;
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
 * 单管理员账号 Mapper
 */
@Mapper
public interface AdminUserMapper {

    @Results(id = "adminUserResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "singletonKey", column = "singleton_key"),
            @Result(property = "username", column = "username"),
            @Result(property = "passwordHash", column = "password_hash"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "lastLoginAt", column = "last_login_at"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time")
    })
    @Select("""
            SELECT id, singleton_key, username, password_hash, enabled, last_login_at, create_time, update_time
            FROM admin_user
            WHERE singleton_key = 'A'
            LIMIT 1
            """)
    AdminUserDO selectCurrentAdmin();

    @Select("SELECT COUNT(1) FROM admin_user")
    long countAll();

    @Insert("""
            INSERT INTO admin_user (
                singleton_key, username, password_hash, enabled, last_login_at, create_time, update_time
            ) VALUES (
                #{singletonKey}, #{username}, #{passwordHash}, #{enabled}, #{lastLoginAt}, #{createTime}, #{updateTime}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminUserDO record);

    @Update("""
            UPDATE admin_user
            SET username = #{username},
                update_time = #{updateTime}
            WHERE id = #{id}
            """)
    int updateUsername(@Param("id") Long id,
                       @Param("username") String username,
                       @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE admin_user
            SET password_hash = #{passwordHash},
                update_time = #{updateTime}
            WHERE id = #{id}
            """)
    int updatePasswordHash(@Param("id") Long id,
                           @Param("passwordHash") String passwordHash,
                           @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE admin_user
            SET last_login_at = #{lastLoginAt},
                update_time = #{updateTime}
            WHERE id = #{id}
            """)
    int updateLastLoginAt(@Param("id") Long id,
                          @Param("lastLoginAt") LocalDateTime lastLoginAt,
                          @Param("updateTime") LocalDateTime updateTime);
}
