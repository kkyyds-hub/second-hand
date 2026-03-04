package com.demo.mapper;

import com.demo.entity.UserOauthBind;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 用户第三方绑定 Mapper。
 *
 * 口径说明：
 * 1) 本表是 OAuth 绑定真源；
 * 2) Redis 仅用于 provider+externalId -> userId 的读加速；
 * 3) 写入/修正逻辑必须先落 DB，再刷新缓存。
 */
@Mapper
public interface UserOauthBindMapper {

    /**
     * 按 provider + externalId 查询“已绑定”记录。
     */
    UserOauthBind selectActiveByProviderAndExternalId(@Param("provider") String provider,
                                                      @Param("externalId") String externalId);

    /**
     * 插入绑定记录（严格写入，冲突由唯一约束兜底）。
     */
    int insert(UserOauthBind bind);

    /**
     * 插入绑定记录（忽略冲突）。
     *
     * 用途：
     * - 用于 Redis 老数据懒迁移回填；
     * - 若记录已存在，返回 0，不抛异常。
     */
    int insertIgnore(UserOauthBind bind);

    /**
     * 更新最近登录时间。
     */
    int touchLoginTime(@Param("provider") String provider,
                       @Param("externalId") String externalId,
                       @Param("lastLoginTime") LocalDateTime lastLoginTime);
}

