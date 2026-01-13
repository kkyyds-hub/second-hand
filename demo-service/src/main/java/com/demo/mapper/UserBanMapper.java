package com.demo.mapper;

import com.demo.entity.UserBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserBanMapper {

    /**
     * 插入一条封禁记录
     */
    void insertUserBan(UserBan userBan);

    int closeActiveBans(@Param("userId") Long userId, @Param("now") LocalDateTime now);

}
