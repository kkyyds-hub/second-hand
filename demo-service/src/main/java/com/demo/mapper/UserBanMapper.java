package com.demo.mapper;

import com.demo.entity.UserBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
/**
 * UserBanMapper 接口。
 */
public interface UserBanMapper {

    /**
     * 插入一条封禁记录
     */
    void insertUserBan(UserBan userBan);

    /**
     * 关闭用户当前有效封禁记录。
     */
    int closeActiveBans(@Param("userId") Long userId, @Param("now") LocalDateTime now);

}
